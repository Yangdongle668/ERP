package com.erp.module.inventory.service.doc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialStockAttr;
import com.erp.module.engineering.api.material.Tracking;
import com.erp.module.inventory.api.InventoryErrorCodes;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.StockOutConfirmedEvent;
import com.erp.module.inventory.api.doc.StockOutRequest;
import com.erp.module.inventory.api.doc.StockOutType;
import com.erp.module.inventory.api.stock.BatchSuggestion;
import com.erp.module.inventory.api.stock.StockDirection;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.inventory.config.InventoryModuleConfig;
import com.erp.module.inventory.controller.vo.StockDocVOs.AllocatedLine;
import com.erp.module.inventory.controller.vo.StockDocVOs.BatchResult;
import com.erp.module.inventory.controller.vo.StockDocVOs.DocQuery;
import com.erp.module.inventory.controller.vo.StockDocVOs.DocRow;
import com.erp.module.inventory.controller.vo.StockDocVOs.Failure;
import com.erp.module.inventory.controller.vo.StockDocVOs.QuickCounts;
import com.erp.module.inventory.controller.vo.StockDocVOs.StockOutDetail;
import com.erp.module.inventory.controller.vo.StockDocVOs.StockOutLineResp;
import com.erp.module.inventory.controller.vo.StockDocVOs.StockOutLineSave;
import com.erp.module.inventory.controller.vo.StockDocVOs.StockOutSave;
import com.erp.module.inventory.dal.dataobject.LocationDO;
import com.erp.module.inventory.dal.dataobject.StockDO;
import com.erp.module.inventory.dal.dataobject.StockOutDO;
import com.erp.module.inventory.dal.dataobject.StockOutLineDO;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.mapper.StockMapper;
import com.erp.module.inventory.dal.mapper.StockOutLineMapper;
import com.erp.module.inventory.dal.mapper.StockOutMapper;
import com.erp.module.inventory.service.InventoryQueryService;
import com.erp.module.inventory.service.posting.PostingModels;
import com.erp.module.inventory.service.posting.PostingModels.PostLine;
import com.erp.module.inventory.service.posting.StockPostingService;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 出库单（需求 08-04）。核心是分配批次/库位：业务生成的出库单只有物料和数量，仓管员确认时选择或自动分配批次；
 * 一个来源行可以拆成多行（不同批次），实发数量按来源行合计校验（R03）。
 */
@Service
public class StockOutService {

    public static final String BIZ_TYPE = "INV_STOCK_OUT";
    public static final String APPROVAL_TYPE = "INV_OTHER_OUT";
    static final String PARAM_AUTO_CONFIRM = "inv.out.auto-confirm-source";
    public static final String REASON_SCRAP = "SCRAP";

    static final Map<StockOutType, String> TYPE_NAMES = Map.of(StockOutType.PRODUCTION_ISSUE, "生产领料出库", StockOutType.OUTSOURCE_ISSUE, "委外发料出库",
            StockOutType.SALES_OUT, "销售出库", StockOutType.PURCHASE_RETURN, "采购退货出库", StockOutType.OTHER_OUT, "其他出库",
            StockOutType.COUNT_LOSS, "盘亏出库");

    /** 出库类型允许的仓库类型（1.1 节、R02） */
    static Set<WarehouseType> allowed(StockOutType t, String reason) {
        return switch (t) {
            case PRODUCTION_ISSUE, OUTSOURCE_ISSUE, SALES_OUT -> DocSupport.AVAILABLE;
            case PURCHASE_RETURN -> DocSupport.with(DocSupport.AVAILABLE, WarehouseType.NG, WarehouseType.QC);
            case OTHER_OUT -> REASON_SCRAP.equals(reason) ? DocSupport.with(DocSupport.AVAILABLE, WarehouseType.NG) : DocSupport.AVAILABLE;
            case COUNT_LOSS -> EnumSet.allOf(WarehouseType.class);
        };
    }

    /** 冻结、过期批次也能出：报废、退供应商、盘亏 */
    static boolean allowFrozen(StockOutDO d) {
        return d.getOutType() == StockOutType.PURCHASE_RETURN || d.getOutType() == StockOutType.COUNT_LOSS
                || (d.getOutType() == StockOutType.OTHER_OUT && REASON_SCRAP.equals(d.getReason()));
    }

    private final StockOutMapper docMapper;
    private final StockOutLineMapper lineMapper;
    private final StockMapper stockMapper;
    private final StockPostingService postingService;
    private final InventoryQueryService queryService;
    private final DocSupport support;
    private final ParamApi paramApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate tx;

    public StockOutService(StockOutMapper docMapper, StockOutLineMapper lineMapper, StockMapper stockMapper, StockPostingService postingService,
                           InventoryQueryService queryService, DocSupport support, ParamApi paramApi, WorkflowApi workflowApi, FileApi fileApi,
                           DomainEventPublisher eventPublisher, PlatformTransactionManager transactionManager) {
        this.docMapper = docMapper;
        this.lineMapper = lineMapper;
        this.stockMapper = stockMapper;
        this.postingService = postingService;
        this.queryService = queryService;
        this.support = support;
        this.paramApi = paramApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ==================== 业务生成 ====================

    /** InventoryDocApi.createStockOut：仓库为空时取物料默认仓，按仓库拆分为多张 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createFromSource(StockOutRequest req, LocalDate sourceDate) {
        SourceRef src = req.source();
        if (src == null || src.sourceType() == null || src.sourceId() == null) throw BizException.of(InventoryErrorCodes.LINE_FIELD_REQUIRED, 1, "来源单据");
        if (req.lines() == null || req.lines().isEmpty()) throw new BizException(InventoryErrorCodes.NO_LINES);
        Map<Long, MaterialDTO> materials = support.materials(req.lines().stream().map(StockOutRequest.Line::materialId).toList());
        Map<Long, List<StockOutRequest.Line>> byWarehouse = new LinkedHashMap<>();
        for (StockOutRequest.Line l : req.lines()) {
            if (l.qty() == null || l.qty().signum() <= 0) throw new BizException(InventoryErrorCodes.QTY_NOT_POSITIVE);
            Long wid = req.warehouseId() != null ? req.warehouseId() : support.warehouses().getDefaultWarehouse(l.materialId(), null).id();
            byWarehouse.computeIfAbsent(wid, k -> new ArrayList<>()).add(l);
        }
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, List<StockOutRequest.Line>> e : byWarehouse.entrySet()) {
            WarehouseDO w = support.warehouse(e.getKey());
            checkWarehouse(req.outType(), null, w);
            StockOutDO d = new StockOutDO();
            d.setDocNo(support.nextNo(InventoryModuleConfig.CODE_STOCK_OUT));
            d.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
            d.setStatus(DocStatus.DRAFT);
            d.setOutType(req.outType());
            d.setWarehouseId(w.getId());
            d.setReceiverDeptId(req.receiverDeptId());
            d.setReceiverId(req.receiverId());
            d.setSupplierId(req.supplierId());
            d.setCustomerId(req.customerId());
            d.setSourceType(src.sourceType());
            d.setSourceId(src.sourceId());
            d.setSourceNo(src.sourceNo());
            d.setSourceDate(sourceDate);
            d.setManual(false);
            d.setOwnerId(support.currentUser());
            docMapper.insert(d);
            int no = 0;
            for (StockOutRequest.Line l : e.getValue()) {
                MaterialDTO m = materials.get(l.materialId());
                if (m == null) throw BizException.of(InventoryErrorCodes.MATERIAL_NOT_USABLE, String.valueOf(l.materialId()));
                StockOutLineDO line = new StockOutLineDO();
                line.setStockOutId(d.getId());
                line.setLineNo(++no);
                line.setMaterialId(m.id());
                line.setUom(StringUtils.hasText(l.uom()) ? l.uom() : m.baseUom());
                line.setRequestQty(l.qty());
                line.setQty(l.qty());
                line.setBaseQty(support.toBase(m, l.qty(), line.getUom()));
                line.setBatchNo(trim(l.batchNo()));
                line.setSerialNos(DocSupport.serialText(l.serialNos()));
                line.setSourceLineId(l.sourceLineId());
                lineMapper.insert(line);
            }
            ids.add(d.getId());
            if (paramApi.getBool(PARAM_AUTO_CONFIRM)) tryAutoConfirm(d);
        }
        return ids;
    }

    /** R10：自动分配后库存充足且不需要序列号时自动确认；否则保持草稿由仓管员处理 */
    private void tryAutoConfirm(StockOutDO d) {
        List<StockOutLineDO> lines = lineMapper.selectByDoc(d.getId());
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(StockOutLineDO::getMaterialId).toList());
        if (lines.stream().anyMatch(l -> materials.get(l.getMaterialId()).tracking() == Tracking.SERIAL)) return;
        List<AllocatedLine> allocated = allocate(d, lines, materials);
        if (allocated.stream().anyMatch(a -> a.shortage() != null && a.shortage().signum() > 0)) return;
        replaceLines(d, allocated.stream().map(a -> new StockOutLineSave(a.id(), a.materialId(), a.uom(), a.requestQty(), a.qty(), a.locationId(),
                a.batchNo(), null, a.sourceLineId(), null)).toList(), materials);
        confirm(d.getId(), null, null);
    }

    /** R09：来源单据撤销：未确认的作废，已确认的阻止 */
    @Transactional(rollbackFor = Exception.class)
    public void cancelBySource(String sourceType, Long sourceId) {
        List<StockOutDO> docs = docMapper.selectList(new LambdaQueryWrapper<StockOutDO>().eq(StockOutDO::getSourceType, sourceType)
                .eq(StockOutDO::getSourceId, sourceId).ne(StockOutDO::getStatus, DocStatus.VOIDED));
        for (StockOutDO d : docs) {
            if (d.getStatus() == DocStatus.COMPLETED) throw BizException.of(InventoryErrorCodes.SOURCE_DOC_CONFIRMED, "出库单", d.getDocNo(), "出库");
        }
        for (StockOutDO d : docs) fire(d, InvDocAction.VOID, "来源单据撤销");
    }

    // ==================== 手工其他出库 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(StockOutSave req) {
        WarehouseDO w = support.warehouse(req.warehouseId());
        support.warehouses().checkAccess(w.getId());
        StockOutDO d = new StockOutDO();
        d.setDocNo(support.nextNo(InventoryModuleConfig.CODE_STOCK_OUT));
        d.setOutType(StockOutType.OTHER_OUT);
        d.setStatus(DocStatus.DRAFT);
        d.setManual(true);
        d.setOwnerId(support.currentUser());
        StockInService.LoginOrg.fill(d);
        fillManual(d, w, req);
        docMapper.insert(d);
        saveManualLines(d, req.lines());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, d.getId());
        return d.getId();
    }

    private void fillManual(StockOutDO d, WarehouseDO w, StockOutSave req) {
        if (!StringUtils.hasText(req.reason())) throw BizException.of(InventoryErrorCodes.OTHER_REASON_REQUIRED, "出库");
        d.setReason(req.reason());
        checkWarehouse(StockOutType.OTHER_OUT, req.reason(), w);
        d.setWarehouseId(w.getId());
        d.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        DocSupport.checkDate(d.getDocDate(), null, "出库");
        d.setReceiverDeptId(req.receiverDeptId());
        d.setReceiverId(req.receiverId());
        d.setRemark(trim(req.remark()));
    }

    private void saveManualLines(StockOutDO d, List<StockOutLineSave> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(InventoryErrorCodes.NO_LINES);
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(StockOutLineSave::materialId).toList());
        lineMapper.deleteByDoc(d.getId());
        int no = 0;
        for (StockOutLineSave l : lines) {
            no++;
            if (l.materialId() == null) throw BizException.of(InventoryErrorCodes.LINE_FIELD_REQUIRED, no, "物料");
            if (l.qty() == null || l.qty().signum() <= 0) throw BizException.of(InventoryErrorCodes.LINE_FIELD_REQUIRED, no, "数量");
            MaterialDTO m = materials.get(l.materialId());
            if (m == null) throw BizException.of(InventoryErrorCodes.MATERIAL_NOT_USABLE, String.valueOf(l.materialId()));
            StockOutLineDO line = new StockOutLineDO();
            line.setStockOutId(d.getId());
            line.setLineNo(no);
            line.setMaterialId(m.id());
            line.setUom(StringUtils.hasText(l.uom()) ? l.uom() : m.baseUom());
            line.setQty(l.qty());
            line.setRequestQty(l.qty());
            line.setBaseQty(support.toBase(m, l.qty(), line.getUom()));
            line.setLocationId(l.locationId());
            line.setBatchNo(trim(l.batchNo()));
            line.setSerialNos(DocSupport.serialText(l.serialNos()));
            line.setRemark(trim(l.remark()));
            lineMapper.insert(line);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, StockOutSave req) {
        StockOutDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        if (d.getStatus() != DocStatus.DRAFT) throw new BizException(InventoryErrorCodes.DOC_NOT_EDITABLE);
        if (req.version() != null) d.setVersion(req.version());
        if (Boolean.TRUE.equals(d.getManual())) {
            WarehouseDO w = support.warehouse(req.warehouseId());
            support.warehouses().checkAccess(w.getId());
            fillManual(d, w, req);
            docMapper.updateByIdOrFail(d);
            saveManualLines(d, req.lines());
        } else {
            if (req.docDate() != null) {
                DocSupport.checkDate(req.docDate(), d.getSourceDate(), "出库");
                d.setDocDate(req.docDate());
            }
            d.setReceiverDeptId(req.receiverDeptId() != null ? req.receiverDeptId() : d.getReceiverDeptId());
            d.setReceiverId(req.receiverId() != null ? req.receiverId() : d.getReceiverId());
            d.setRemark(trim(req.remark()));
            docMapper.updateByIdOrFail(d);
            if (req.lines() != null) {
                List<StockOutLineDO> existing = lineMapper.selectByDoc(id);
                replaceLines(d, req.lines(), support.materials(existing.stream().map(StockOutLineDO::getMaterialId).toList()));
            }
        }
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, d.getId());
    }

    /**
     * 业务生成的单据：按来源行重新拆分批次行。物料、申请数量由原单据决定，前端只能改实发数量、库位、批次、序列号、备注。
     * 前端拆分出来的行带原行的 id 或 sourceLineId。
     */
    private void replaceLines(StockOutDO d, List<StockOutLineSave> lines, Map<Long, MaterialDTO> materials) {
        List<StockOutLineDO> existing = lineMapper.selectByDoc(d.getId());
        Map<Long, StockOutLineDO> byId = existing.stream().collect(Collectors.toMap(StockOutLineDO::getId, l -> l));
        Map<Long, StockOutLineDO> bySource = new HashMap<>();
        existing.stream().filter(l -> l.getSourceLineId() != null).forEach(l -> bySource.putIfAbsent(l.getSourceLineId(), l));
        List<StockOutLineDO> rebuilt = new ArrayList<>();
        Set<StockOutLineDO> covered = new HashSet<>();
        for (StockOutLineSave l : lines) {
            StockOutLineDO origin = l.sourceLineId() != null ? bySource.get(l.sourceLineId()) : l.id() == null ? null : byId.get(l.id());
            if (origin == null || (l.materialId() != null && !l.materialId().equals(origin.getMaterialId()))) {
                throw new BizException(InventoryErrorCodes.DOC_QTY_LOCKED);
            }
            covered.add(origin);
            MaterialDTO m = materials.get(origin.getMaterialId());
            StockOutLineDO line = new StockOutLineDO();
            line.setStockOutId(d.getId());
            line.setMaterialId(origin.getMaterialId());
            line.setSourceLineId(origin.getSourceLineId());
            BigDecimal qty = l.qty() == null ? BigDecimal.ZERO : l.qty();
            String uom = StringUtils.hasText(l.uom()) ? l.uom() : origin.getUom();
            if (uom.equals(origin.getUom())) {
                line.setUom(uom);
                line.setRequestQty(origin.getRequestQty());
            } else {
                // 拆分行以基本单位表示，申请数量同步换算
                line.setUom(m.baseUom());
                line.setRequestQty(support.toBase(m, origin.getRequestQty(), origin.getUom()));
            }
            line.setQty(qty);
            line.setBaseQty(support.toBase(m, qty, line.getUom()));
            line.setLocationId(l.locationId());
            line.setBatchNo(trim(l.batchNo()));
            line.setSerialNos(DocSupport.serialText(l.serialNos()));
            line.setRemark(trim(l.remark()));
            rebuilt.add(line);
        }
        // 前端没有提交的来源行保留原样（实发数量为 0 的行也要保留，以便来源行合计）
        for (StockOutLineDO o : existing) {
            boolean kept = covered.contains(o) || (o.getSourceLineId() != null && covered.stream().anyMatch(c -> o.getSourceLineId().equals(c.getSourceLineId())));
            if (!kept) {
                StockOutLineDO copy = new StockOutLineDO();
                copy.setStockOutId(d.getId());
                copy.setMaterialId(o.getMaterialId());
                copy.setSourceLineId(o.getSourceLineId());
                copy.setUom(o.getUom());
                copy.setRequestQty(o.getRequestQty());
                copy.setQty(o.getQty());
                copy.setBaseQty(o.getBaseQty());
                copy.setLocationId(o.getLocationId());
                copy.setBatchNo(o.getBatchNo());
                copy.setSerialNos(o.getSerialNos());
                copy.setRemark(o.getRemark());
                rebuilt.add(copy);
            }
        }
        lineMapper.deleteByDoc(d.getId());
        int no = 0;
        for (StockOutLineDO l : rebuilt) {
            l.setLineNo(++no);
            lineMapper.insert(l);
        }
    }

    // ==================== 批次分配 ====================

    /** [自动分配批次]：返回分配后的行（不保存） */
    public List<AllocatedLine> autoAllocate(Long id, List<StockOutLineSave> current) {
        StockOutDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        List<StockOutLineDO> lines = lineMapper.selectByDoc(id);
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(StockOutLineDO::getMaterialId).toList());
        if (current != null && !current.isEmpty()) {
            // 页面上未保存的修改：按当前行分配
            Map<Long, StockOutLineDO> byId = lines.stream().collect(Collectors.toMap(StockOutLineDO::getId, l -> l));
            List<StockOutLineDO> tmp = new ArrayList<>();
            for (StockOutLineSave s : current) {
                StockOutLineDO origin = s.id() == null ? null : byId.get(s.id());
                StockOutLineDO l = new StockOutLineDO();
                l.setId(s.id());
                l.setMaterialId(origin != null ? origin.getMaterialId() : s.materialId());
                if (l.getMaterialId() == null) continue;
                l.setSourceLineId(origin != null ? origin.getSourceLineId() : s.sourceLineId());
                l.setUom(StringUtils.hasText(s.uom()) ? s.uom() : origin != null ? origin.getUom() : null);
                l.setRequestQty(origin != null && !Boolean.TRUE.equals(d.getManual()) ? origin.getRequestQty() : s.requestQty() != null ? s.requestQty() : s.qty());
                l.setQty(s.qty() == null ? BigDecimal.ZERO : s.qty());
                l.setLocationId(s.locationId());
                l.setBatchNo(trim(s.batchNo()));
                tmp.add(l);
            }
            materials = support.materials(tmp.stream().map(StockOutLineDO::getMaterialId).toList());
            for (StockOutLineDO l : tmp) {
                MaterialDTO m = materials.get(l.getMaterialId());
                if (m == null) throw BizException.of(InventoryErrorCodes.MATERIAL_NOT_USABLE, String.valueOf(l.getMaterialId()));
                if (l.getUom() == null) l.setUom(m.baseUom());
                l.setBaseQty(support.toBase(m, l.getQty(), l.getUom()));
            }
            lines = tmp;
        }
        return allocate(d, lines, materials);
    }

    /**
     * 按物料出库规则（FIFO/FEFO，R06）为未指定批次（批次物料）或未指定库位（启用库位的仓库）的行分配；
     * 数量不够一个批次时拆成多行，库存不足时剩余数量保留在一行并返回 shortage。
     */
    List<AllocatedLine> allocate(StockOutDO d, List<StockOutLineDO> lines, Map<Long, MaterialDTO> materials) {
        WarehouseDO w = support.warehouse(d.getWarehouseId());
        boolean locEnabled = Boolean.TRUE.equals(w.getLocationEnabled());
        boolean frozenOk = allowFrozen(d);
        // 已明确指定批次/库位的行先占用库存
        Map<String, BigDecimal> taken = new HashMap<>();
        for (StockOutLineDO l : lines) {
            if (!needsAllocation(l, materials.get(l.getMaterialId()), locEnabled) && l.getBaseQty() != null) {
                taken.merge(key(l.getMaterialId(), l.getLocationId(), l.getBatchNo()), l.getBaseQty(), BigDecimal::add);
            }
        }
        Map<Long, List<BatchSuggestion>> pools = new HashMap<>();
        Set<Long> locIds = new HashSet<>();
        List<AllocatedLine> out = new ArrayList<>();
        for (StockOutLineDO l : lines) {
            MaterialDTO m = materials.get(l.getMaterialId());
            if (!needsAllocation(l, m, locEnabled) || l.getBaseQty() == null || l.getBaseQty().signum() <= 0) {
                out.add(new AllocatedLine(l.getId(), l.getMaterialId(), m.code(), l.getUom(), l.getRequestQty(), l.getQty(), l.getLocationId(), null,
                        l.getBatchNo(), l.getSourceLineId(), null));
                continue;
            }
            List<BatchSuggestion> pool = pools.computeIfAbsent(m.id(), k -> candidates(m.id(), d.getWarehouseId(), frozenOk, taken));
            BigDecimal rest = l.getBaseQty();
            boolean sameUom = l.getUom().equals(m.baseUom());
            BigDecimal request = sameUom ? l.getRequestQty() : support.toBase(m, l.getRequestQty(), l.getUom());
            List<AllocatedLine> parts = new ArrayList<>();
            for (int i = 0; i < pool.size() && rest.signum() > 0; i++) {
                BatchSuggestion s = pool.get(i);
                if (s.qty().signum() <= 0) continue;
                BigDecimal take = s.qty().min(rest);
                pool.set(i, new BatchSuggestion(s.batchNo(), s.locationId(), s.qty().subtract(take), s.productionDate(), s.expireDate()));
                rest = rest.subtract(take);
                if (s.locationId() != null) locIds.add(s.locationId());
                parts.add(new AllocatedLine(l.getId(), m.id(), m.code(), m.baseUom(), request, take, s.locationId(), null, s.batchNo(),
                        l.getSourceLineId(), null));
            }
            if (rest.signum() > 0) {
                parts.add(new AllocatedLine(l.getId(), m.id(), m.code(), m.baseUom(), request, rest, null, null, null, l.getSourceLineId(), rest));
            }
            if (parts.size() == 1 && sameUom && parts.get(0).shortage() == null) {
                AllocatedLine p = parts.get(0);
                parts.set(0, new AllocatedLine(p.id(), p.materialId(), p.materialCode(), l.getUom(), l.getRequestQty(), l.getQty(), p.locationId(), null,
                        p.batchNo(), p.sourceLineId(), null));
            }
            out.addAll(parts);
        }
        lines.stream().map(StockOutLineDO::getLocationId).filter(Objects::nonNull).forEach(locIds::add);
        Map<Long, LocationDO> locs = support.warehouses().locationsByIds(locIds);
        return out.stream().map(a -> a.locationId() == null || !locs.containsKey(a.locationId()) ? a
                : new AllocatedLine(a.id(), a.materialId(), a.materialCode(), a.uom(), a.requestQty(), a.qty(), a.locationId(),
                locs.get(a.locationId()).getCode(), a.batchNo(), a.sourceLineId(), a.shortage())).toList();
    }

    private static boolean needsAllocation(StockOutLineDO l, MaterialDTO m, boolean locEnabled) {
        boolean batch = m.tracking() == Tracking.BATCH;
        return (batch && !StringUtils.hasText(l.getBatchNo())) || (locEnabled && l.getLocationId() == null);
    }

    /** 可分配的库存（按 FIFO/FEFO 排好序），扣除本单已指定批次占用的数量 */
    private List<BatchSuggestion> candidates(Long materialId, Long warehouseId, boolean frozenOk, Map<String, BigDecimal> taken) {
        List<BatchSuggestion> list = new ArrayList<>();
        if (frozenOk) {
            // 报废、退供应商：冻结、过期批次也可以出，按批次号、库位排序
            stockMapper.selectList(new LambdaQueryWrapper<StockDO>().eq(StockDO::getMaterialId, materialId).eq(StockDO::getWarehouseId, warehouseId)
                            .gt(StockDO::getQty, 0).orderByAsc(StockDO::getBatchNo).orderByAsc(StockDO::getLocationId))
                    .forEach(s -> list.add(new BatchSuggestion(s.getBatchNo().isEmpty() ? null : s.getBatchNo(),
                            s.getLocationId() == 0 ? null : s.getLocationId(), s.getQty(), null, null)));
        } else {
            list.addAll(queryService.suggestBatches(materialId, warehouseId, new BigDecimal("999999999999")));
        }
        List<BatchSuggestion> result = new ArrayList<>();
        for (BatchSuggestion s : list) {
            BigDecimal used = taken.getOrDefault(key(materialId, s.locationId(), s.batchNo()), BigDecimal.ZERO);
            BigDecimal left = s.qty().subtract(used);
            if (used.signum() > 0) taken.put(key(materialId, s.locationId(), s.batchNo()), used.subtract(s.qty()).max(BigDecimal.ZERO));
            if (left.signum() > 0) result.add(new BatchSuggestion(s.batchNo(), s.locationId(), left, s.productionDate(), s.expireDate()));
        }
        return result;
    }

    private static String key(Long materialId, Long locationId, String batchNo) {
        return materialId + "|" + (locationId == null ? 0 : locationId) + "|" + (batchNo == null ? "" : batchNo);
    }

    // ==================== 提交与审批（手工其他出库） ====================

    @Transactional(rollbackFor = Exception.class)
    public String submit(Long id) {
        StockOutDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        if (!Boolean.TRUE.equals(d.getManual())) throw new BizException(InventoryErrorCodes.DOC_NOT_EDITABLE);
        checkScrap(d);
        List<StockOutLineDO> lines = lineMapper.selectByDoc(id);
        BigDecimal amount = BigDecimal.ZERO;
        for (StockOutLineDO l : lines) {
            BigDecimal cost = support.refCost(l.getMaterialId());
            if (cost != null && l.getBaseQty() != null) amount = amount.add(DocSupport.amount(l.getBaseQty(), cost));
        }
        StartResult r = workflowApi.start(APPROVAL_TYPE, d.getId(), d.getDocNo(), "其他出库 " + d.getDocNo(),
                Map.of("reason", d.getReason(), "amountBase", amount), Map.of(), SecurityUtils.getLoginUserIdOrNull());
        if (r.isStarted()) {
            fire(d, InvDocAction.SUBMIT, null);
            return d.getStatus().name();
        }
        fire(d, InvDocAction.APPROVE, null);
        confirm(id, null, null);
        return DocStatus.COMPLETED.name();
    }

    /** R09：报废出库需要附件或备注说明 */
    private void checkScrap(StockOutDO d) {
        if (d.getOutType() == StockOutType.OTHER_OUT && REASON_SCRAP.equals(d.getReason()) && !StringUtils.hasText(d.getRemark())
                && fileApi.list(BIZ_TYPE, d.getId()).isEmpty()) {
            throw new BizException(InventoryErrorCodes.SCRAP_NEEDS_EXPLAIN);
        }
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!APPROVAL_TYPE.equals(e.getBizType())) return;
        StockOutDO d = getOrThrow(e.getBizId());
        if (d.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> fire(d, InvDocAction.APPROVE, null);
            case WITHDRAWN -> fire(d, InvDocAction.WITHDRAW, null);
            default -> fire(d, InvDocAction.REJECT, e.getComment());
        }
    }

    // ==================== 确认 ====================

    /** 确认出库：可带分配结果；校验仓库（R02）、数量（R03）、批次库位序列号（R04）→ 过账 → 发布 StockOutConfirmedEvent（R05） */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, LocalDate docDate, List<StockOutLineSave> outLines) {
        confirm(id, docDate, outLines, true);
    }

    /** @param checkAccess 系统生成（盘点、期初）的单据由审核人确认，不校验仓库数据权限 */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, LocalDate docDate, List<StockOutLineSave> outLines, boolean checkAccess) {
        StockOutDO d = getOrThrow(id);
        if (checkAccess) support.warehouses().checkAccess(d.getWarehouseId());
        boolean manual = Boolean.TRUE.equals(d.getManual());
        if (manual ? d.getStatus() != DocStatus.APPROVED : d.getStatus() != DocStatus.DRAFT) {
            throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, d.getStatus().label(), "确认出库");
        }
        if (docDate != null) {
            DocSupport.checkDate(docDate, d.getSourceDate(), "出库");
            d.setDocDate(docDate);
        }
        if (outLines != null && !outLines.isEmpty()) {
            List<StockOutLineDO> existing = lineMapper.selectByDoc(id);
            Map<Long, MaterialDTO> ms = support.materials(existing.stream().map(StockOutLineDO::getMaterialId).toList());
            if (manual) {
                // 已审核的手工单只能补充库位、批次、序列号（可拆分批次行，物料数量合计不变）
                Map<Long, BigDecimal> before = sumByMaterial(existing);
                replaceLines(d, outLines.stream().map(l -> new StockOutLineSave(l.id(), l.materialId(), l.uom(), null, l.qty(), l.locationId(),
                        l.batchNo(), l.serialNos(), null, l.remark())).toList(), ms);
                if (!before.equals(sumByMaterial(lineMapper.selectByDoc(id)))) throw new BizException(InventoryErrorCodes.DOC_QTY_LOCKED);
            } else {
                replaceLines(d, outLines, ms);
            }
        }
        List<StockOutLineDO> lines = lineMapper.selectByDoc(id);
        WarehouseDO w = support.warehouse(d.getWarehouseId());
        checkWarehouse(d.getOutType(), d.getReason(), w);
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(StockOutLineDO::getMaterialId).toList());
        if (!manual) checkQty(d, lines, materials);
        String missing = missingInfo(w, lines, materials);
        if (missing != null) throw new BizException(InventoryErrorCodes.LINE_FIELD_REQUIRED.code(), missing);
        List<StockOutLineDO> posting = lines.stream().filter(l -> l.getBaseQty() != null && l.getBaseQty().signum() > 0).toList();
        if (posting.isEmpty()) throw new BizException(InventoryErrorCodes.QTY_NOT_POSITIVE);
        boolean frozenOk = allowFrozen(d);
        List<PostLine> post = posting.stream().map(l -> new PostLine(l.getId(), l.getSourceLineId(), StockDirection.OUT, l.getMaterialId(),
                d.getWarehouseId(), l.getLocationId(), l.getBatchNo(), l.getBaseQty(), null, DocSupport.serials(l.getSerialNos()), frozenOk, null)).toList();
        postingService.post(new PostingModels.PostCommand(PostingModels.DOC_STOCK_OUT, d.getOutType().name(), d.getId(), d.getDocNo(),
                d.getSourceType(), d.getSourceId(), d.getSourceNo(), d.getDocDate(), post, d.getOutType() == StockOutType.COUNT_LOSS, false,
                d.getOutType() == StockOutType.SALES_OUT ? d.getCustomerId() : null));
        d.setConfirmedBy(SecurityUtils.getLoginUserIdOrNull());
        d.setConfirmedAt(LocalDateTime.now());
        fire(d, InvDocAction.CONFIRM, null);
        eventPublisher.publish(new StockOutConfirmedEvent(d.getId(), d.getDocNo(), d.getOutType(), source(d), d.getWarehouseId(),
                posting.stream().map(l -> new StockOutConfirmedEvent.Line(l.getSourceLineId(), l.getMaterialId(), l.getBatchNo(), l.getBaseQty())).toList()));
    }

    private static Map<Long, BigDecimal> sumByMaterial(List<StockOutLineDO> lines) {
        Map<Long, BigDecimal> m = new HashMap<>();
        lines.forEach(l -> m.merge(l.getMaterialId(), l.getBaseQty().stripTrailingZeros(), (a, b) -> a.add(b).stripTrailingZeros()));
        return m;
    }

    /** R03：同一来源行拆分的多行合计：领料/委外发料不能多于申请；销售出库、采购退货必须等于申请 */
    private void checkQty(StockOutDO d, List<StockOutLineDO> lines, Map<Long, MaterialDTO> materials) {
        Map<String, List<StockOutLineDO>> groups = new LinkedHashMap<>();
        for (StockOutLineDO l : lines) groups.computeIfAbsent(l.getSourceLineId() != null ? "S" + l.getSourceLineId() : "L" + l.getId(), k -> new ArrayList<>()).add(l);
        boolean mustEqual = d.getOutType() == StockOutType.SALES_OUT || d.getOutType() == StockOutType.PURCHASE_RETURN;
        for (List<StockOutLineDO> g : groups.values()) {
            MaterialDTO m = materials.get(g.get(0).getMaterialId());
            BigDecimal request = g.stream().map(l -> support.toBase(m, l.getRequestQty(), l.getUom())).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal actual = g.stream().map(StockOutLineDO::getBaseQty).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (mustEqual && actual.compareTo(request) != 0) {
                throw BizException.of(InventoryErrorCodes.OUT_QTY_MUST_EQUAL, TYPE_NAMES.get(d.getOutType()), request.stripTrailingZeros().toPlainString());
            }
            if (actual.compareTo(request) > 0) throw BizException.of(InventoryErrorCodes.OUT_QTY_EXCEED, g.get(0).getLineNo());
        }
    }

    /** R04：批次、库位、序列号按物料属性必填，齐全时返回 null */
    private String missingInfo(WarehouseDO w, List<StockOutLineDO> lines, Map<Long, MaterialDTO> materials) {
        for (StockOutLineDO l : lines) {
            if (l.getBaseQty() == null || l.getBaseQty().signum() <= 0) continue;
            MaterialDTO m = materials.get(l.getMaterialId());
            if (m.tracking() == Tracking.BATCH && !StringUtils.hasText(l.getBatchNo())) return "第 " + l.getLineNo() + " 行：请选择批次";
            if (Boolean.TRUE.equals(w.getLocationEnabled()) && l.getLocationId() == null) return "第 " + l.getLineNo() + " 行：库位不能为空";
            if (m.tracking() == Tracking.SERIAL) {
                int n = DocSupport.serials(l.getSerialNos()).size();
                if (BigDecimal.valueOf(n).compareTo(l.getBaseQty()) != 0) {
                    return "第 " + l.getLineNo() + " 行：序列号不能为空（需要 " + l.getBaseQty().stripTrailingZeros().toPlainString() + " 个，已录 " + n + " 个）";
                }
            }
        }
        return null;
    }

    public BatchResult batchConfirm(List<Long> ids) {
        int ok = 0;
        List<Failure> failures = new ArrayList<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            StockOutDO d = docMapper.selectById(id);
            if (d == null) continue;
            try {
                tx.executeWithoutResult(s -> confirm(id, null, null));
                ok++;
            } catch (BizException e) {
                failures.add(new Failure(id, d.getDocNo(), e.getMessage()));
            }
        }
        return new BatchResult(ok, failures);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        StockOutDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        if (!StringUtils.hasText(reason)) throw new BizException(InventoryErrorCodes.REJECT_REASON_REQUIRED);
        if (Boolean.TRUE.equals(d.getManual())) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, "手工单据", "退回");
        d.setRejectReason(reason.trim());
        fire(d, InvDocAction.RETURN, reason.trim());
        eventPublisher.publish(new StockDocEvent(PostingModels.DOC_STOCK_OUT, StockDocEvent.Kind.REJECTED, d.getId(), d.getDocNo(), source(d), reason.trim()));
    }

    /** R08：来源模块可阻止（如已开票）；冲销库存；业务单回到草稿，手工单回到已审核 */
    @Transactional(rollbackFor = Exception.class)
    public void unconfirm(Long id, String reason) {
        StockOutDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        if (!StringUtils.hasText(reason)) throw new BizException(InventoryErrorCodes.UNCONFIRM_REASON_REQUIRED);
        if (d.getStatus() != DocStatus.COMPLETED) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, d.getStatus().label(), "反确认");
        if (d.getOutType() == StockOutType.COUNT_LOSS) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, TYPE_NAMES.get(d.getOutType()), "反确认");
        eventPublisher.publish(new StockDocEvent(PostingModels.DOC_STOCK_OUT, StockDocEvent.Kind.OUT_REVERSING, d.getId(), d.getDocNo(), source(d), reason.trim()));
        Map<Long, List<String>> serials = new HashMap<>();
        lineMapper.selectByDoc(id).forEach(l -> serials.put(l.getId(), DocSupport.serials(l.getSerialNos())));
        postingService.reverse(PostingModels.DOC_STOCK_OUT, d.getId(), d.getDocNo(), serials);
        d.setConfirmedBy(null);
        d.setConfirmedAt(null);
        fire(d, Boolean.TRUE.equals(d.getManual()) ? InvDocAction.UNCONFIRM_MANUAL : InvDocAction.UNCONFIRM, reason.trim());
        eventPublisher.publish(new StockDocEvent(PostingModels.DOC_STOCK_OUT, StockDocEvent.Kind.OUT_REVERSED, d.getId(), d.getDocNo(), source(d), reason.trim()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        StockOutDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        if (!Boolean.TRUE.equals(d.getManual())) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, "业务生成的单据", "作废");
        fire(d, InvDocAction.VOID, reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        StockOutDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        if (!Boolean.TRUE.equals(d.getManual()) || d.getStatus() != DocStatus.DRAFT) throw new BizException(InventoryErrorCodes.DOC_NOT_EDITABLE);
        lineMapper.deleteByDoc(id);
        docMapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    /** 盘亏等系统生成的出库单，生成后由调用方确认 */
    @Transactional(rollbackFor = Exception.class)
    public Long createSystem(StockOutType type, Long warehouseId, LocalDate docDate, String sourceType, Long sourceId, String sourceNo, String remark,
                             List<StockOutLineDO> lines) {
        StockOutDO d = new StockOutDO();
        d.setDocNo(support.nextNo(InventoryModuleConfig.CODE_STOCK_OUT));
        d.setDocDate(docDate);
        d.setStatus(DocStatus.DRAFT);
        d.setOutType(type);
        d.setWarehouseId(warehouseId);
        d.setSourceType(sourceType);
        d.setSourceId(sourceId);
        d.setSourceNo(sourceNo);
        d.setRemark(remark);
        d.setManual(false);
        d.setOwnerId(support.currentUser());
        docMapper.insert(d);
        int no = 0;
        for (StockOutLineDO l : lines) {
            l.setStockOutId(d.getId());
            l.setLineNo(++no);
            lineMapper.insert(l);
        }
        return d.getId();
    }

    private void fire(StockOutDO d, InvDocAction action, String reason) {
        DocStatus old = d.getStatus();
        d.setStatus(InvDocAction.MACHINE.fire(old, action));
        docMapper.updateByIdOrFail(d);
        support.log(BIZ_TYPE, d.getId(), d.getDocNo(), action, action == InvDocAction.CONFIRM ? "确认出库" : null, old, d.getStatus(), reason);
    }

    private void checkWarehouse(StockOutType type, String reason, WarehouseDO w) {
        Set<WarehouseType> allowed = allowed(type, reason);
        if (!allowed.contains(w.getWarehouseType())) {
            throw BizException.of(InventoryErrorCodes.OUT_WAREHOUSE_TYPE, TYPE_NAMES.get(type), DocSupport.typeNames(allowed));
        }
    }

    public StockOutDO getOrThrow(Long id) {
        StockOutDO d = id == null ? null : docMapper.selectById(id);
        if (d == null) throw new BizException(InventoryErrorCodes.DOC_NOT_EXISTS);
        return d;
    }

    static SourceRef source(StockOutDO d) {
        return new SourceRef(d.getSourceType(), d.getSourceId(), d.getSourceNo());
    }

    // ==================== 查询 ====================

    public PageResult<DocRow> page(DocQuery q) {
        LambdaQueryWrapper<StockOutDO> w = query(q);
        if (w == null) return PageResult.empty();
        PageResult<StockOutDO> page = docMapper.selectPage(q, w);
        return new PageResult<>(rows(page.list()), page.total());
    }

    public List<DocRow> listForExport(DocQuery q, int limit) {
        LambdaQueryWrapper<StockOutDO> w = query(q);
        return w == null ? List.of() : rows(docMapper.selectList(w.last("LIMIT " + limit)));
    }

    public QuickCounts quickCounts() {
        Set<Long> allowed = support.warehouses().accessibleIds();
        if (allowed != null && allowed.isEmpty()) return new QuickCounts(0, 0);
        long todo = docMapper.selectCount(new LambdaQueryWrapper<StockOutDO>().in(allowed != null, StockOutDO::getWarehouseId, allowed)
                .and(x -> x.nested(n -> n.eq(StockOutDO::getManual, false).eq(StockOutDO::getStatus, DocStatus.DRAFT))
                        .or().eq(StockOutDO::getStatus, DocStatus.APPROVED)));
        long today = docMapper.selectCount(new LambdaQueryWrapper<StockOutDO>().in(allowed != null, StockOutDO::getWarehouseId, allowed)
                .eq(StockOutDO::getStatus, DocStatus.COMPLETED).ge(StockOutDO::getConfirmedAt, LocalDate.now().atStartOfDay()));
        return new QuickCounts(todo, today);
    }

    private LambdaQueryWrapper<StockOutDO> query(DocQuery q) {
        Set<Long> allowed = support.warehouses().accessibleIds();
        if (allowed != null && allowed.isEmpty()) return null;
        LambdaQueryWrapper<StockOutDO> w = new LambdaQueryWrapper<StockOutDO>()
                .in(allowed != null, StockOutDO::getWarehouseId, allowed)
                .likeRight(StringUtils.hasText(q.getDocNo()), StockOutDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getWarehouseId() != null, StockOutDO::getWarehouseId, q.getWarehouseId())
                .likeRight(StringUtils.hasText(q.getSourceNo()), StockOutDO::getSourceNo, q.getSourceNo() == null ? null : q.getSourceNo().trim())
                .eq(q.getSupplierId() != null, StockOutDO::getSupplierId, q.getSupplierId())
                .ge(q.getDateFrom() != null, StockOutDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, StockOutDO::getDocDate, q.getDateTo());
        if (StringUtils.hasText(q.getIds())) w.in(StockOutDO::getId, StockInService.ids(q.getIds()));
        if (StringUtils.hasText(q.getTypes())) w.in(StockOutDO::getOutType, Arrays.stream(q.getTypes().split(",")).map(String::trim).map(StockOutType::valueOf).toList());
        if ("TODO".equals(q.getQuick())) {
            w.and(x -> x.nested(n -> n.eq(StockOutDO::getManual, false).eq(StockOutDO::getStatus, DocStatus.DRAFT)).or().eq(StockOutDO::getStatus, DocStatus.APPROVED));
        } else if ("TODAY".equals(q.getQuick())) {
            w.eq(StockOutDO::getStatus, DocStatus.COMPLETED).ge(StockOutDO::getConfirmedAt, LocalDate.now().atStartOfDay());
        } else if (StringUtils.hasText(q.getStatuses())) {
            w.in(StockOutDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        if (q.getMaterialId() != null) {
            w.inSql(StockOutDO::getId, "SELECT stock_out_id FROM inv_stock_out_line WHERE deleted = 0 AND material_id = " + q.getMaterialId().longValue());
        }
        return w.orderByDesc(StockOutDO::getDocDate).orderByDesc(StockOutDO::getId);
    }

    private List<DocRow> rows(List<StockOutDO> docs) {
        if (docs.isEmpty()) return List.of();
        List<StockOutLineDO> lines = lineMapper.selectByDocs(docs.stream().map(StockOutDO::getId).toList());
        Map<Long, List<StockOutLineDO>> byDoc = lines.stream().collect(Collectors.groupingBy(StockOutLineDO::getStockOutId));
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(StockOutLineDO::getMaterialId).toList());
        Map<Long, WarehouseDO> whs = support.warehouses().byIds(docs.stream().map(StockOutDO::getWarehouseId).toList());
        Set<Long> userIds = new HashSet<>();
        docs.forEach(d -> {
            userIds.add(d.getConfirmedBy());
            userIds.add(d.getCreatedBy());
            userIds.add(d.getReceiverId());
        });
        Map<Long, UserDTO> users = support.users(userIds);
        return docs.stream().map(d -> {
            List<StockOutLineDO> ls = byDoc.getOrDefault(d.getId(), List.of());
            WarehouseDO w = whs.get(d.getWarehouseId());
            Set<String> uoms = ls.stream().map(l -> materials.containsKey(l.getMaterialId()) ? materials.get(l.getMaterialId()).baseUom() : "").collect(Collectors.toSet());
            BigDecimal total = uoms.size() == 1 ? ls.stream().map(StockOutLineDO::getBaseQty).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
            return new DocRow(d.getId(), d.getDocNo(), PostingModels.DOC_STOCK_OUT, d.getOutType().name(), d.getWarehouseId(), w == null ? null : w.getName(),
                    w == null ? null : w.getWarehouseType(), null, null, null, d.getSourceType(), d.getSourceId(), d.getSourceNo(),
                    DocSupport.name(users, d.getReceiverId()),
                    DocSupport.summary(null, materials, ls.stream().map(StockOutLineDO::getMaterialId).toList()), ls.size(), total, null, d.getDocDate(),
                    d.getStatus().name(), Boolean.TRUE.equals(d.getManual()), DocSupport.name(users, d.getConfirmedBy()), d.getConfirmedAt(),
                    DocSupport.name(users, d.getCreatedBy()));
        }).toList();
    }

    public StockOutDetail detail(Long id) {
        StockOutDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        List<StockOutLineDO> lines = lineMapper.selectByDoc(id);
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(StockOutLineDO::getMaterialId).toList());
        Map<Long, LocationDO> locs = support.warehouses().locationsByIds(lines.stream().map(StockOutLineDO::getLocationId).toList());
        WarehouseDO w = support.warehouse(d.getWarehouseId());
        Map<Long, UserDTO> users = support.users(List.of(nz(d.getConfirmedBy()), nz(d.getCreatedBy()), nz(d.getReceiverId())));
        Map<Long, BigDecimal> available = new HashMap<>();
        for (Long mid : materials.keySet()) available.put(mid, availableIn(mid, w));
        List<StockOutLineResp> resp = lines.stream().map(l -> {
            MaterialDTO m = materials.get(l.getMaterialId());
            MaterialStockAttr a = m == null ? null : support.stockAttr(m.id());
            return new StockOutLineResp(l.getId(), l.getLineNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.spec(), m == null ? null : m.baseUom(), m == null || m.tracking() == null ? "NONE" : m.tracking().name(),
                    a == null || a.issueRule() == null ? null : a.issueRule().name(), l.getUom(), l.getRequestQty(), l.getQty(), l.getBaseQty(),
                    available.get(l.getMaterialId()), l.getLocationId(), l.getLocationId() != null && locs.containsKey(l.getLocationId())
                    ? locs.get(l.getLocationId()).getCode() : null, l.getBatchNo(), DocSupport.serials(l.getSerialNos()), l.getSourceLineId(), l.getRemark());
        }).toList();
        return new StockOutDetail(d.getId(), d.getDocNo(), d.getOutType(), w.getId(), w.getName(), w.getWarehouseType(),
                Boolean.TRUE.equals(w.getLocationEnabled()), d.getDocDate(), d.getStatus().name(), Boolean.TRUE.equals(d.getManual()), d.getReason(),
                d.getReceiverDeptId(), d.getReceiverId(), DocSupport.name(users, d.getReceiverId()), d.getSourceType(), d.getSourceId(), d.getSourceNo(),
                d.getSourceDate(), d.getSupplierId(), d.getCustomerId(), d.getRemark(), d.getConfirmedBy(), DocSupport.name(users, d.getConfirmedBy()),
                d.getConfirmedAt(), d.getRejectReason(), DocSupport.name(users, d.getCreatedBy()), d.getCreatedAt(), d.getVersion(), resp);
    }

    /** 可用仓取可用量（扣除预留）；不良品仓、待检仓等取现存量 */
    private BigDecimal availableIn(Long materialId, WarehouseDO w) {
        if (w.getWarehouseType().available()) return queryService.getAvailableQty(materialId, w.getId());
        return stockMapper.selectList(new LambdaQueryWrapper<StockDO>().eq(StockDO::getMaterialId, materialId).eq(StockDO::getWarehouseId, w.getId()))
                .stream().map(StockDO::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<String, Object> printData(Long id) {
        StockOutDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("docDate", d.docDate());
        data.put("typeName", TYPE_NAMES.get(d.outType()));
        data.put("warehouseName", d.warehouseName());
        data.put("toWarehouseName", "");
        data.put("sourceNo", Objects.toString(d.sourceNo(), ""));
        data.put("partnerName", Objects.toString(d.receiverName(), ""));
        data.put("reasonName", Objects.toString(d.reason(), ""));
        data.put("remark", Objects.toString(d.remark(), ""));
        data.put("statusName", DocStatus.valueOf(d.status()).label());
        data.put("confirmedByName", Objects.toString(d.confirmedByName(), ""));
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (StockOutLineResp l : d.lines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.lineNo());
            m.put("code", l.materialCode());
            m.put("name", l.materialName());
            m.put("spec", Objects.toString(l.materialSpec(), ""));
            m.put("uom", l.uom());
            m.put("qty", l.qty());
            m.put("batchNo", Objects.toString(l.batchNo(), ""));
            m.put("locationCode", Objects.toString(l.locationCode(), ""));
            m.put("remark", Objects.toString(l.remark(), ""));
            lines.add(m);
            total = total.add(l.qty());
        }
        data.put("lines", lines);
        data.put("totalQty", total);
        return data;
    }

    private static Long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
