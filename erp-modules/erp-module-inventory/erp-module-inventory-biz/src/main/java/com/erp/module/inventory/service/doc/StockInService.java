package com.erp.module.inventory.service.doc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialStockAttr;
import com.erp.module.engineering.api.material.Tracking;
import com.erp.module.inventory.api.InventoryErrorCodes;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.StockInConfirmedEvent;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.stock.StockDirection;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.inventory.config.InventoryModuleConfig;
import com.erp.module.inventory.controller.vo.StockDocVOs.BatchResult;
import com.erp.module.inventory.controller.vo.StockDocVOs.DocQuery;
import com.erp.module.inventory.controller.vo.StockDocVOs.DocRow;
import com.erp.module.inventory.controller.vo.StockDocVOs.Failure;
import com.erp.module.inventory.controller.vo.StockDocVOs.QuickCounts;
import com.erp.module.inventory.controller.vo.StockDocVOs.StockInDetail;
import com.erp.module.inventory.controller.vo.StockDocVOs.StockInLineResp;
import com.erp.module.inventory.controller.vo.StockDocVOs.StockInLineSave;
import com.erp.module.inventory.controller.vo.StockDocVOs.StockInSave;
import com.erp.module.inventory.dal.dataobject.LocationDO;
import com.erp.module.inventory.dal.dataobject.StockInDO;
import com.erp.module.inventory.dal.dataobject.StockInLineDO;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.mapper.StockInLineMapper;
import com.erp.module.inventory.dal.mapper.StockInMapper;
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
 * 入库单（需求 08-03）。业务模块通过 InventoryDocApi 生成草稿，仓管员确认后过账；只有“其他入库”可以手工新建。
 */
@Service
public class StockInService {

    public static final String BIZ_TYPE = "INV_STOCK_IN";
    public static final String APPROVAL_TYPE = "INV_OTHER_IN";
    static final String PARAM_AUTO_CONFIRM = "inv.in.auto-confirm-source";

    /** 入库类型允许的仓库类型（R03） */
    static Set<WarehouseType> allowed(StockInType t) {
        return switch (t) {
            case PURCHASE_IN, OUTSOURCE_IN, PRODUCTION_IN -> DocSupport.with(DocSupport.AVAILABLE, WarehouseType.QC);
            case OUTSOURCE_RETURN, PRODUCTION_RETURN, OTHER_IN -> DocSupport.with(DocSupport.AVAILABLE, WarehouseType.NG);
            case SALES_RETURN -> EnumSet.of(WarehouseType.RTN);
            case COUNT_GAIN, OPENING -> EnumSet.allOf(WarehouseType.class);
        };
    }

    static final Map<StockInType, String> TYPE_NAMES = Map.of(StockInType.PURCHASE_IN, "采购入库", StockInType.OUTSOURCE_IN, "委外入库",
            StockInType.OUTSOURCE_RETURN, "委外退料入库", StockInType.PRODUCTION_IN, "生产入库", StockInType.PRODUCTION_RETURN, "生产退料入库",
            StockInType.SALES_RETURN, "销售退货入库", StockInType.OTHER_IN, "其他入库", StockInType.COUNT_GAIN, "盘盈入库", StockInType.OPENING, "期初入库");

    private final StockInMapper docMapper;
    private final StockInLineMapper lineMapper;
    private final StockPostingService postingService;
    private final DocSupport support;
    private final MaterialApi materialApi;
    private final ParamApi paramApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate tx;

    public StockInService(StockInMapper docMapper, StockInLineMapper lineMapper, StockPostingService postingService, DocSupport support,
                          MaterialApi materialApi, ParamApi paramApi, WorkflowApi workflowApi, FileApi fileApi, DomainEventPublisher eventPublisher,
                          PlatformTransactionManager transactionManager) {
        this.docMapper = docMapper;
        this.lineMapper = lineMapper;
        this.postingService = postingService;
        this.support = support;
        this.materialApi = materialApi;
        this.paramApi = paramApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ==================== 业务生成 ====================

    /** InventoryDocApi.createStockIn：仓库为空时按规则确定（需检 → 待检仓；免检 → 物料默认仓），按仓库拆分为多张 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createFromSource(StockInRequest req, LocalDate sourceDate) {
        SourceRef src = req.source();
        if (src == null || src.sourceType() == null || src.sourceId() == null) throw BizException.of(InventoryErrorCodes.LINE_FIELD_REQUIRED, 1, "来源单据");
        if (req.lines() == null || req.lines().isEmpty()) throw new BizException(InventoryErrorCodes.NO_LINES);
        checkSourceLinesFree(src, req.lines().stream().map(StockInRequest.Line::sourceLineId).filter(Objects::nonNull).toList());
        Map<Long, MaterialDTO> materials = support.materials(req.lines().stream().map(StockInRequest.Line::materialId).toList());
        Map<Long, List<StockInRequest.Line>> byWarehouse = new LinkedHashMap<>();
        for (StockInRequest.Line l : req.lines()) {
            Long wid = req.warehouseId() != null ? req.warehouseId() : routeWarehouse(req.inType(), l.materialId());
            byWarehouse.computeIfAbsent(wid, k -> new ArrayList<>()).add(l);
        }
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, List<StockInRequest.Line>> e : byWarehouse.entrySet()) {
            WarehouseDO w = support.warehouse(e.getKey());
            checkWarehouse(req.inType(), w);
            StockInDO d = new StockInDO();
            d.setDocNo(support.nextNo(InventoryModuleConfig.CODE_STOCK_IN));
            d.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
            d.setStatus(DocStatus.DRAFT);
            d.setInType(req.inType());
            d.setWarehouseId(w.getId());
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
            for (StockInRequest.Line l : e.getValue()) {
                MaterialDTO m = materials.get(l.materialId());
                if (m == null) throw BizException.of(InventoryErrorCodes.MATERIAL_NOT_USABLE, String.valueOf(l.materialId()));
                StockInLineDO line = new StockInLineDO();
                line.setStockInId(d.getId());
                line.setLineNo(++no);
                line.setMaterialId(m.id());
                line.setUom(StringUtils.hasText(l.uom()) ? l.uom() : m.baseUom());
                line.setQty(l.qty());
                line.setBaseQty(support.toBase(m, l.qty(), line.getUom()));
                line.setBatchNo(trim(l.batchNo()));
                line.setSupplierBatchNo(trim(l.supplierBatchNo()));
                line.setProductionDate(l.productionDate());
                line.setExpireDate(expire(m, l.productionDate(), null));
                line.setSerialNos(DocSupport.serialText(l.serialNos()));
                line.setUnitCost(l.unitCost());
                line.setAmount(DocSupport.amount(line.getBaseQty(), l.unitCost()));
                line.setSourceLineId(l.sourceLineId());
                lineMapper.insert(line);
            }
            ids.add(d.getId());
            // R10：参数允许且必填信息齐全时自动确认
            if (paramApi.getBool(PARAM_AUTO_CONFIRM) && missingInfo(d, lineMapper.selectByDoc(d.getId())) == null) confirm(d.getId(), null, null);
        }
        return ids;
    }

    private Long routeWarehouse(StockInType type, Long materialId) {
        WarehouseType fixed = switch (type) {
            case SALES_RETURN -> WarehouseType.RTN;
            case PURCHASE_IN, OUTSOURCE_IN -> materialApi.getQualityAttr(materialId).iqcRequired() ? WarehouseType.QC : null;
            case PRODUCTION_IN -> materialApi.getQualityAttr(materialId).fqcRequired() ? WarehouseType.QC : null;
            default -> null;
        };
        return support.warehouses().getDefaultWarehouse(materialId, fixed).id();
    }

    /** R01：同一来源行不能被两张未作废的入库单同时引用 */
    private void checkSourceLinesFree(SourceRef src, List<Long> sourceLineIds) {
        if (sourceLineIds.isEmpty()) return;
        List<StockInDO> docs = docMapper.selectList(new LambdaQueryWrapper<StockInDO>().eq(StockInDO::getSourceType, src.sourceType())
                .eq(StockInDO::getSourceId, src.sourceId()).ne(StockInDO::getStatus, DocStatus.VOIDED));
        if (docs.isEmpty()) return;
        Map<Long, StockInDO> byId = docs.stream().collect(Collectors.toMap(StockInDO::getId, d -> d));
        for (StockInLineDO l : lineMapper.selectByDocs(byId.keySet())) {
            if (l.getSourceLineId() != null && sourceLineIds.contains(l.getSourceLineId())) {
                throw BizException.of(InventoryErrorCodes.SOURCE_LINE_DUPLICATE, byId.get(l.getStockInId()).getDocNo());
            }
        }
    }

    /** R09：来源单据撤销：未确认的作废，已确认的阻止 */
    @Transactional(rollbackFor = Exception.class)
    public void cancelBySource(String sourceType, Long sourceId) {
        List<StockInDO> docs = docMapper.selectList(new LambdaQueryWrapper<StockInDO>().eq(StockInDO::getSourceType, sourceType)
                .eq(StockInDO::getSourceId, sourceId).ne(StockInDO::getStatus, DocStatus.VOIDED));
        for (StockInDO d : docs) {
            if (d.getStatus() == DocStatus.COMPLETED) throw BizException.of(InventoryErrorCodes.SOURCE_DOC_CONFIRMED, "入库单", d.getDocNo(), "入库");
        }
        for (StockInDO d : docs) fire(d, InvDocAction.VOID, "来源单据撤销");
    }

    // ==================== 手工其他入库 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(StockInSave req) {
        WarehouseDO w = support.warehouse(req.warehouseId());
        support.warehouses().checkAccess(w.getId());
        checkWarehouse(StockInType.OTHER_IN, w);
        StockInDO d = new StockInDO();
        d.setDocNo(support.nextNo(InventoryModuleConfig.CODE_STOCK_IN));
        d.setInType(StockInType.OTHER_IN);
        d.setStatus(DocStatus.DRAFT);
        d.setManual(true);
        d.setOwnerId(support.currentUser());
        LoginOrg.fill(d);
        fillManual(d, w, req);
        docMapper.insert(d);
        saveManualLines(d, req.lines());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, d.getId());
        return d.getId();
    }

    private void fillManual(StockInDO d, WarehouseDO w, StockInSave req) {
        if (!StringUtils.hasText(req.reason())) throw BizException.of(InventoryErrorCodes.OTHER_REASON_REQUIRED, "入库");
        d.setWarehouseId(w.getId());
        d.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        DocSupport.checkDate(d.getDocDate(), null, "入库");
        d.setReason(req.reason());
        d.setRemark(trim(req.remark()));
    }

    private void saveManualLines(StockInDO d, List<StockInLineSave> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(InventoryErrorCodes.NO_LINES);
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(StockInLineSave::materialId).toList());
        lineMapper.deleteByDoc(d.getId());
        int no = 0;
        for (StockInLineSave l : lines) {
            no++;
            if (l.materialId() == null) throw BizException.of(InventoryErrorCodes.LINE_FIELD_REQUIRED, no, "物料");
            if (l.qty() == null || l.qty().signum() <= 0) throw BizException.of(InventoryErrorCodes.LINE_FIELD_REQUIRED, no, "数量");
            MaterialDTO m = materials.get(l.materialId());
            if (m == null) throw BizException.of(InventoryErrorCodes.MATERIAL_NOT_USABLE, String.valueOf(l.materialId()));
            StockInLineDO line = new StockInLineDO();
            line.setStockInId(d.getId());
            line.setLineNo(no);
            line.setMaterialId(m.id());
            line.setUom(StringUtils.hasText(l.uom()) ? l.uom() : m.baseUom());
            line.setQty(l.qty());
            line.setBaseQty(support.toBase(m, l.qty(), line.getUom()));
            applySupplement(line, l, m);
            line.setUnitCost(l.unitCost());
            line.setAmount(DocSupport.amount(line.getBaseQty(), l.unitCost()));
            lineMapper.insert(line);
        }
    }

    /** 可补充的字段：库位、批次、供应商批号、生产/到期日期、序列号、备注 */
    private void applySupplement(StockInLineDO line, StockInLineSave l, MaterialDTO m) {
        line.setLocationId(l.locationId());
        line.setBatchNo(trim(l.batchNo()));
        line.setSupplierBatchNo(trim(l.supplierBatchNo()));
        line.setProductionDate(l.productionDate());
        line.setExpireDate(expire(m, l.productionDate(), l.expireDate()));
        line.setSerialNos(DocSupport.serialText(l.serialNos()));
        line.setRemark(trim(l.remark()));
    }

    /** 到期日期 = 生产日期 + 保质期（未手工填写时） */
    private LocalDate expire(MaterialDTO m, LocalDate production, LocalDate given) {
        if (given != null || production == null) return given;
        MaterialStockAttr a = materialApi.getStockAttr(m.id());
        return a.shelfLifeDays() == null ? null : production.plusDays(a.shelfLifeDays());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, StockInSave req) {
        StockInDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        if (d.getStatus() != DocStatus.DRAFT) throw new BizException(InventoryErrorCodes.DOC_NOT_EDITABLE);
        if (req.version() != null) d.setVersion(req.version());
        if (Boolean.TRUE.equals(d.getManual())) {
            WarehouseDO w = support.warehouse(req.warehouseId());
            support.warehouses().checkAccess(w.getId());
            checkWarehouse(StockInType.OTHER_IN, w);
            fillManual(d, w, req);
            docMapper.updateByIdOrFail(d);
            saveManualLines(d, req.lines());
        } else {
            if (req.docDate() != null) {
                DocSupport.checkDate(req.docDate(), d.getSourceDate(), "入库");
                d.setDocDate(req.docDate());
            }
            d.setRemark(trim(req.remark()));
            docMapper.updateByIdOrFail(d);
            supplement(d, req.lines());
        }
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, d.getId());
    }

    /** 业务生成的单据：按行 ID 补充信息，不能改物料和数量 */
    private void supplement(StockInDO d, List<StockInLineSave> lines) {
        if (lines == null) return;
        Map<Long, StockInLineDO> existing = lineMapper.selectByDoc(d.getId()).stream().collect(Collectors.toMap(StockInLineDO::getId, l -> l));
        Map<Long, MaterialDTO> materials = support.materials(existing.values().stream().map(StockInLineDO::getMaterialId).toList());
        for (StockInLineSave l : lines) {
            StockInLineDO line = l.id() == null ? null : existing.get(l.id());
            if (line == null) throw new BizException(InventoryErrorCodes.DOC_QTY_LOCKED);
            if ((l.materialId() != null && !l.materialId().equals(line.getMaterialId())) || (l.qty() != null && l.qty().compareTo(line.getQty()) != 0)) {
                throw new BizException(InventoryErrorCodes.DOC_QTY_LOCKED);
            }
            applySupplement(line, l, materials.get(line.getMaterialId()));
            lineMapper.updateByIdOrFail(line);
        }
    }

    /** 提交（手工其他入库）：发起审批；未配置审批流时直接审核并确认入库 */
    @Transactional(rollbackFor = Exception.class)
    public String submit(Long id) {
        StockInDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        if (!Boolean.TRUE.equals(d.getManual())) throw new BizException(InventoryErrorCodes.DOC_NOT_EDITABLE);
        List<StockInLineDO> lines = lineMapper.selectByDoc(id);
        BigDecimal amount = lines.stream().map(StockInLineDO::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        StartResult r = workflowApi.start(APPROVAL_TYPE, d.getId(), d.getDocNo(), "其他入库 " + d.getDocNo(),
                Map.of("reason", d.getReason(), "amountBase", amount), Map.of(), SecurityUtils.getLoginUserIdOrNull());
        if (r.isStarted()) {
            fire(d, InvDocAction.SUBMIT, null);
            return d.getStatus().name();
        }
        fire(d, InvDocAction.APPROVE, null);
        confirm(id, null, null);
        return DocStatus.COMPLETED.name();
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!APPROVAL_TYPE.equals(e.getBizType())) return;
        StockInDO d = getOrThrow(e.getBizId());
        if (d.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> fire(d, InvDocAction.APPROVE, null);
            case WITHDRAWN -> fire(d, InvDocAction.WITHDRAW, null);
            default -> fire(d, InvDocAction.REJECT, e.getComment());
        }
    }

    // ==================== 确认 ====================

    /**
     * 确认入库：可带补充信息；校验必填（R04）→ 生成批次号 → 过账 → 状态已入库 → 发布 StockInConfirmedEvent。
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, LocalDate docDate, List<StockInLineSave> supplementLines) {
        confirm(id, docDate, supplementLines, true);
    }

    /** @param checkAccess 系统生成（盘点、期初）的单据由审核人确认，不校验仓库数据权限 */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, LocalDate docDate, List<StockInLineSave> supplementLines, boolean checkAccess) {
        StockInDO d = getOrThrow(id);
        if (checkAccess) support.warehouses().checkAccess(d.getWarehouseId());
        boolean manual = Boolean.TRUE.equals(d.getManual());
        if (manual ? d.getStatus() != DocStatus.APPROVED : d.getStatus() != DocStatus.DRAFT) {
            throw BizException.of(com.erp.common.exception.GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, d.getStatus().label(), "确认入库");
        }
        if (docDate != null) {
            DocSupport.checkDate(docDate, d.getSourceDate(), "入库");
            d.setDocDate(docDate);
        }
        if (supplementLines != null && !supplementLines.isEmpty()) {
            if (manual) {
                Map<Long, StockInLineDO> byId = lineMapper.selectByDoc(id).stream().collect(Collectors.toMap(StockInLineDO::getId, l -> l));
                Map<Long, MaterialDTO> ms = support.materials(byId.values().stream().map(StockInLineDO::getMaterialId).toList());
                for (StockInLineSave l : supplementLines) {
                    StockInLineDO line = l.id() == null ? null : byId.get(l.id());
                    if (line == null) continue;
                    applySupplement(line, l, ms.get(line.getMaterialId()));
                    lineMapper.updateByIdOrFail(line);
                }
            } else {
                supplement(d, supplementLines);
            }
        }
        List<StockInLineDO> lines = lineMapper.selectByDoc(id);
        WarehouseDO w = support.warehouse(d.getWarehouseId());
        checkWarehouse(d.getInType(), w);
        String missing = missingInfo(d, lines);
        if (missing != null) throw new BizException(InventoryErrorCodes.LINE_FIELD_REQUIRED.code(), missing);
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(StockInLineDO::getMaterialId).toList());
        // 批次管理物料未填批次号时自动生成（生产入库默认使用生产订单号）
        for (StockInLineDO l : lines) {
            MaterialDTO m = materials.get(l.getMaterialId());
            if (m.tracking() == Tracking.BATCH && !StringUtils.hasText(l.getBatchNo())) {
                l.setBatchNo(d.getInType() == StockInType.PRODUCTION_IN && StringUtils.hasText(d.getSourceNo())
                        ? d.getSourceNo() : support.nextNo(InventoryModuleConfig.CODE_BATCH));
                lineMapper.updateByIdOrFail(l);
            }
        }
        List<PostLine> post = lines.stream().map(l -> new PostLine(l.getId(), l.getSourceLineId(), StockDirection.IN, l.getMaterialId(),
                d.getWarehouseId(), l.getLocationId(), l.getBatchNo(), l.getBaseQty(), unitCost(l), DocSupport.serials(l.getSerialNos()), false,
                new PostingModels.BatchAttrs(d.getSupplierId(), l.getSupplierBatchNo(), l.getProductionDate(), l.getExpireDate()))).toList();
        postingService.post(new PostingModels.PostCommand(PostingModels.DOC_STOCK_IN, d.getInType().name(), d.getId(), d.getDocNo(),
                d.getSourceType(), d.getSourceId(), d.getSourceNo(), d.getDocDate(), post, d.getInType() == StockInType.COUNT_GAIN,
                d.getInType() == StockInType.OPENING, null));
        d.setConfirmedBy(SecurityUtils.getLoginUserIdOrNull());
        d.setConfirmedAt(LocalDateTime.now());
        fire(d, InvDocAction.CONFIRM, null);
        eventPublisher.publish(new StockInConfirmedEvent(d.getId(), d.getDocNo(), d.getInType(), source(d), d.getWarehouseId(),
                w.getWarehouseType().name(), lines.stream().map(l -> new StockInConfirmedEvent.Line(l.getSourceLineId(), l.getMaterialId(),
                l.getBatchNo(), l.getBaseQty())).toList()));
    }

    /** 单价换算到基本单位：行单价按基本单位录入 */
    private static BigDecimal unitCost(StockInLineDO l) {
        return l.getUnitCost();
    }

    /** 缺少的必填信息（R04），齐全时返回 null */
    String missingInfo(StockInDO d, List<StockInLineDO> lines) {
        WarehouseDO w = support.warehouse(d.getWarehouseId());
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(StockInLineDO::getMaterialId).toList());
        for (StockInLineDO l : lines) {
            MaterialDTO m = materials.get(l.getMaterialId());
            if (Boolean.TRUE.equals(w.getLocationEnabled()) && l.getLocationId() == null) return "第 " + l.getLineNo() + " 行：库位不能为空";
            if (m != null && m.tracking() == Tracking.SERIAL) {
                int n = DocSupport.serials(l.getSerialNos()).size();
                if (BigDecimal.valueOf(n).compareTo(l.getBaseQty()) != 0) {
                    return "第 " + l.getLineNo() + " 行：序列号不能为空（需要 " + l.getBaseQty().stripTrailingZeros().toPlainString() + " 个，已录 " + n + " 个）";
                }
            }
            if (m != null && m.tracking() == Tracking.BATCH && l.getProductionDate() == null && d.getInType() != StockInType.COUNT_GAIN && d.getInType() != StockInType.OPENING
                    && materialApi.getStockAttr(m.id()).shelfLifeDays() != null) {
                return "第 " + l.getLineNo() + " 行：生产日期不能为空";
            }
        }
        return null;
    }

    public BatchResult batchConfirm(List<Long> ids) {
        int ok = 0;
        List<Failure> failures = new ArrayList<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            StockInDO d = docMapper.selectById(id);
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

    /** 退回（业务生成的草稿）：作废并通知来源模块（R06） */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        StockInDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        if (!StringUtils.hasText(reason)) throw new BizException(InventoryErrorCodes.REJECT_REASON_REQUIRED);
        if (Boolean.TRUE.equals(d.getManual())) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, "手工单据", "退回");
        d.setRejectReason(reason.trim());
        fire(d, InvDocAction.RETURN, reason.trim());
        eventPublisher.publish(new StockDocEvent(PostingModels.DOC_STOCK_IN, StockDocEvent.Kind.REJECTED, d.getId(), d.getDocNo(), source(d), reason.trim()));
    }

    /** 反确认（R07、R08）：来源模块可阻止；冲销库存；业务单回到草稿，手工单回到已审核 */
    @Transactional(rollbackFor = Exception.class)
    public void unconfirm(Long id, String reason) {
        StockInDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        if (!StringUtils.hasText(reason)) throw new BizException(InventoryErrorCodes.UNCONFIRM_REASON_REQUIRED);
        if (d.getStatus() != DocStatus.COMPLETED) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, d.getStatus().label(), "反确认");
        if (d.getInType() == StockInType.COUNT_GAIN || d.getInType() == StockInType.OPENING) {
            throw BizException.of(com.erp.common.exception.GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, TYPE_NAMES.get(d.getInType()), "反确认");
        }
        eventPublisher.publish(new StockDocEvent(PostingModels.DOC_STOCK_IN, StockDocEvent.Kind.IN_REVERSING, d.getId(), d.getDocNo(), source(d), reason.trim()));
        Map<Long, List<String>> serials = new HashMap<>();
        lineMapper.selectByDoc(id).forEach(l -> serials.put(l.getId(), DocSupport.serials(l.getSerialNos())));
        postingService.reverse(PostingModels.DOC_STOCK_IN, d.getId(), d.getDocNo(), serials);
        d.setConfirmedBy(null);
        d.setConfirmedAt(null);
        fire(d, Boolean.TRUE.equals(d.getManual()) ? InvDocAction.UNCONFIRM_MANUAL : InvDocAction.UNCONFIRM, reason.trim());
        eventPublisher.publish(new StockDocEvent(PostingModels.DOC_STOCK_IN, StockDocEvent.Kind.IN_REVERSED, d.getId(), d.getDocNo(), source(d), reason.trim()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        StockInDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        if (!Boolean.TRUE.equals(d.getManual())) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, "业务生成的单据", "作废");
        fire(d, InvDocAction.VOID, reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        StockInDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        if (!Boolean.TRUE.equals(d.getManual()) || d.getStatus() != DocStatus.DRAFT) throw new BizException(InventoryErrorCodes.DOC_NOT_EDITABLE);
        lineMapper.deleteByDoc(id);
        docMapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    private void fire(StockInDO d, InvDocAction action, String reason) {
        DocStatus old = d.getStatus();
        d.setStatus(InvDocAction.MACHINE.fire(old, action));
        docMapper.updateByIdOrFail(d);
        support.log(BIZ_TYPE, d.getId(), d.getDocNo(), action, action == InvDocAction.CONFIRM ? "确认入库" : null, old, d.getStatus(), reason);
    }

    private void checkWarehouse(StockInType type, WarehouseDO w) {
        Set<WarehouseType> allowed = allowed(type);
        if (!allowed.contains(w.getWarehouseType())) {
            throw BizException.of(InventoryErrorCodes.IN_WAREHOUSE_TYPE, TYPE_NAMES.get(type), DocSupport.typeNames(allowed));
        }
    }

    public StockInDO getOrThrow(Long id) {
        StockInDO d = id == null ? null : docMapper.selectById(id);
        if (d == null) throw new BizException(InventoryErrorCodes.DOC_NOT_EXISTS);
        return d;
    }

    static SourceRef source(StockInDO d) {
        return new SourceRef(d.getSourceType(), d.getSourceId(), d.getSourceNo());
    }

    // ==================== 查询 ====================

    public PageResult<DocRow> page(DocQuery q) {
        LambdaQueryWrapper<StockInDO> w = query(q);
        if (w == null) return PageResult.empty();
        PageResult<StockInDO> page = docMapper.selectPage(q, w);
        return new PageResult<>(rows(page.list()), page.total());
    }

    public List<DocRow> listForExport(DocQuery q, int limit) {
        LambdaQueryWrapper<StockInDO> w = query(q);
        return w == null ? List.of() : rows(docMapper.selectList(w.last("LIMIT " + limit)));
    }

    public QuickCounts quickCounts() {
        Set<Long> allowed = support.warehouses().accessibleIds();
        if (allowed != null && allowed.isEmpty()) return new QuickCounts(0, 0);
        long todo = docMapper.selectCount(new LambdaQueryWrapper<StockInDO>().in(allowed != null, StockInDO::getWarehouseId, allowed)
                .and(x -> x.nested(n -> n.eq(StockInDO::getManual, false).eq(StockInDO::getStatus, DocStatus.DRAFT))
                        .or().eq(StockInDO::getStatus, DocStatus.APPROVED)));
        long today = docMapper.selectCount(new LambdaQueryWrapper<StockInDO>().in(allowed != null, StockInDO::getWarehouseId, allowed)
                .eq(StockInDO::getStatus, DocStatus.COMPLETED).ge(StockInDO::getConfirmedAt, LocalDate.now().atStartOfDay()));
        return new QuickCounts(todo, today);
    }

    private LambdaQueryWrapper<StockInDO> query(DocQuery q) {
        Set<Long> allowed = support.warehouses().accessibleIds();
        if (allowed != null && allowed.isEmpty()) return null;
        LambdaQueryWrapper<StockInDO> w = new LambdaQueryWrapper<StockInDO>()
                .in(allowed != null, StockInDO::getWarehouseId, allowed)
                .likeRight(StringUtils.hasText(q.getDocNo()), StockInDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getWarehouseId() != null, StockInDO::getWarehouseId, q.getWarehouseId())
                .likeRight(StringUtils.hasText(q.getSourceNo()), StockInDO::getSourceNo, q.getSourceNo() == null ? null : q.getSourceNo().trim())
                .eq(q.getSupplierId() != null, StockInDO::getSupplierId, q.getSupplierId())
                .ge(q.getDateFrom() != null, StockInDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, StockInDO::getDocDate, q.getDateTo());
        if (StringUtils.hasText(q.getIds())) w.in(StockInDO::getId, ids(q.getIds()));
        if (StringUtils.hasText(q.getTypes())) w.in(StockInDO::getInType, Arrays.stream(q.getTypes().split(",")).map(String::trim).map(StockInType::valueOf).toList());
        if ("TODO".equals(q.getQuick())) {
            w.and(x -> x.nested(n -> n.eq(StockInDO::getManual, false).eq(StockInDO::getStatus, DocStatus.DRAFT)).or().eq(StockInDO::getStatus, DocStatus.APPROVED));
        } else if ("TODAY".equals(q.getQuick())) {
            w.eq(StockInDO::getStatus, DocStatus.COMPLETED).ge(StockInDO::getConfirmedAt, LocalDate.now().atStartOfDay());
        } else if (StringUtils.hasText(q.getStatuses())) {
            w.in(StockInDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        if (q.getMaterialId() != null) {
            w.inSql(StockInDO::getId, "SELECT stock_in_id FROM inv_stock_in_line WHERE deleted = 0 AND material_id = " + q.getMaterialId().longValue());
        }
        return w.orderByDesc(StockInDO::getDocDate).orderByDesc(StockInDO::getId);
    }

    static List<Long> ids(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> s.matches("\\d+")).map(Long::valueOf).toList();
    }

    private List<DocRow> rows(List<StockInDO> docs) {
        if (docs.isEmpty()) return List.of();
        List<StockInLineDO> lines = lineMapper.selectByDocs(docs.stream().map(StockInDO::getId).toList());
        Map<Long, List<StockInLineDO>> byDoc = lines.stream().collect(Collectors.groupingBy(StockInLineDO::getStockInId));
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(StockInLineDO::getMaterialId).toList());
        Map<Long, WarehouseDO> whs = support.warehouses().byIds(docs.stream().map(StockInDO::getWarehouseId).toList());
        Set<Long> userIds = new HashSet<>();
        docs.forEach(d -> {
            userIds.add(d.getConfirmedBy());
            userIds.add(d.getCreatedBy());
        });
        Map<Long, UserDTO> users = support.users(userIds);
        boolean cost = canViewCost();
        return docs.stream().map(d -> {
            List<StockInLineDO> ls = byDoc.getOrDefault(d.getId(), List.of());
            WarehouseDO w = whs.get(d.getWarehouseId());
            Set<String> uoms = ls.stream().map(l -> materials.containsKey(l.getMaterialId()) ? materials.get(l.getMaterialId()).baseUom() : "").collect(Collectors.toSet());
            BigDecimal total = uoms.size() == 1 ? ls.stream().map(StockInLineDO::getBaseQty).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
            BigDecimal amount = cost ? ls.stream().map(StockInLineDO::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
            return new DocRow(d.getId(), d.getDocNo(), PostingModels.DOC_STOCK_IN, d.getInType().name(), d.getWarehouseId(), w == null ? null : w.getName(),
                    w == null ? null : w.getWarehouseType(), null, null, null, d.getSourceType(), d.getSourceId(), d.getSourceNo(), null,
                    DocSupport.summary(null, materials, ls.stream().map(StockInLineDO::getMaterialId).toList()), ls.size(), total, amount, d.getDocDate(),
                    d.getStatus().name(), Boolean.TRUE.equals(d.getManual()), DocSupport.name(users, d.getConfirmedBy()), d.getConfirmedAt(),
                    DocSupport.name(users, d.getCreatedBy()));
        }).toList();
    }

    public StockInDetail detail(Long id) {
        StockInDO d = getOrThrow(id);
        support.warehouses().checkAccess(d.getWarehouseId());
        List<StockInLineDO> lines = lineMapper.selectByDoc(id);
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(StockInLineDO::getMaterialId).toList());
        Map<Long, LocationDO> locs = support.warehouses().locationsByIds(lines.stream().map(StockInLineDO::getLocationId).toList());
        WarehouseDO w = support.warehouse(d.getWarehouseId());
        Map<Long, UserDTO> users = support.users(List.of(nz(d.getConfirmedBy()), nz(d.getCreatedBy())));
        boolean cost = canViewCost();
        List<StockInLineResp> resp = lines.stream().map(l -> {
            MaterialDTO m = materials.get(l.getMaterialId());
            Integer shelf = m == null ? null : materialApi.getStockAttr(m.id()).shelfLifeDays();
            return new StockInLineResp(l.getId(), l.getLineNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.spec(), m == null ? null : m.baseUom(), m == null || m.tracking() == null ? "NONE" : m.tracking().name(), shelf,
                    l.getUom(), l.getQty(), l.getBaseQty(), l.getLocationId(), l.getLocationId() != null && locs.containsKey(l.getLocationId())
                    ? locs.get(l.getLocationId()).getCode() : null, l.getBatchNo(), l.getSupplierBatchNo(), l.getProductionDate(), l.getExpireDate(),
                    DocSupport.serials(l.getSerialNos()), cost ? l.getUnitCost() : null, cost ? l.getAmount() : null, l.getSourceLineId(), l.getRemark());
        }).toList();
        return new StockInDetail(d.getId(), d.getDocNo(), d.getInType(), w.getId(), w.getName(), w.getWarehouseType(), Boolean.TRUE.equals(w.getLocationEnabled()),
                d.getDocDate(), d.getStatus().name(), Boolean.TRUE.equals(d.getManual()), d.getReason(), d.getSourceType(), d.getSourceId(), d.getSourceNo(),
                d.getSourceDate(), d.getSupplierId(), d.getCustomerId(), d.getRemark(), d.getConfirmedBy(), DocSupport.name(users, d.getConfirmedBy()),
                d.getConfirmedAt(), d.getRejectReason(), DocSupport.name(users, d.getCreatedBy()), d.getCreatedAt(), d.getVersion(), resp);
    }

    public Map<String, Object> printData(Long id) {
        StockInDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("docDate", d.docDate());
        data.put("typeName", TYPE_NAMES.get(d.inType()));
        data.put("warehouseName", d.warehouseName());
        data.put("toWarehouseName", "");
        data.put("sourceNo", Objects.toString(d.sourceNo(), ""));
        data.put("partnerName", "");
        data.put("reasonName", Objects.toString(d.reason(), ""));
        data.put("remark", Objects.toString(d.remark(), ""));
        data.put("statusName", DocStatus.valueOf(d.status()).label());
        data.put("confirmedByName", Objects.toString(d.confirmedByName(), ""));
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (StockInLineResp l : d.lines()) {
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

    public static boolean canViewCost() {
        var u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission("inv:stock:cost");
    }

    private static Long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    /** 期初、盘盈等系统生成的入库单（不经过 InventoryDocApi 的来源校验），生成后由调用方确认 */
    @Transactional(rollbackFor = Exception.class)
    public Long createSystem(StockInType type, Long warehouseId, LocalDate docDate, String sourceType, Long sourceId, String sourceNo, String remark,
                             List<StockInLineDO> lines) {
        StockInDO d = new StockInDO();
        d.setDocNo(support.nextNo(InventoryModuleConfig.CODE_STOCK_IN));
        d.setDocDate(docDate);
        d.setStatus(DocStatus.DRAFT);
        d.setInType(type);
        d.setWarehouseId(warehouseId);
        d.setSourceType(sourceType);
        d.setSourceId(sourceId);
        d.setSourceNo(sourceNo);
        d.setRemark(remark);
        d.setManual(false);
        d.setOwnerId(support.currentUser());
        docMapper.insert(d);
        int no = 0;
        for (StockInLineDO l : lines) {
            l.setStockInId(d.getId());
            l.setLineNo(++no);
            lineMapper.insert(l);
        }
        return d.getId();
    }

    /** 清空期初：冲销系统生成的已确认入库单并作废 */
    @Transactional(rollbackFor = Exception.class)
    public void reverseSystem(Long id, String reason) {
        StockInDO d = getOrThrow(id);
        if (d.getStatus() == DocStatus.COMPLETED) {
            Map<Long, List<String>> serials = new HashMap<>();
            lineMapper.selectByDoc(id).forEach(l -> serials.put(l.getId(), DocSupport.serials(l.getSerialNos())));
            postingService.reverse(PostingModels.DOC_STOCK_IN, d.getId(), d.getDocNo(), serials);
            d.setConfirmedBy(null);
            d.setConfirmedAt(null);
            fire(d, InvDocAction.UNCONFIRM, reason);
        }
        if (d.getStatus() != DocStatus.VOIDED) fire(d, InvDocAction.VOID, reason);
    }

    /** 当前登录人的公司、部门写入单据（数据权限字段） */
    static final class LoginOrg {
        private LoginOrg() {
        }

        static void fill(com.erp.framework.mybatis.BaseDocDO d) {
            var u = SecurityUtils.getLoginUserOrNull();
            if (u == null) return;
            d.setOrgId(u.orgId());
            d.setDeptId(u.deptId());
        }
    }
}
