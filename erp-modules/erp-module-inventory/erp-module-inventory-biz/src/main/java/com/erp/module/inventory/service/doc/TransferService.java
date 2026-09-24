package com.erp.module.inventory.service.doc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.Tracking;
import com.erp.module.inventory.api.InventoryErrorCodes;
import com.erp.module.inventory.api.doc.JudgeResult;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.TransferConfirmedEvent;
import com.erp.module.inventory.api.doc.TransferRequest;
import com.erp.module.inventory.api.doc.TransferType;
import com.erp.module.inventory.api.stock.StockDirection;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.inventory.config.InventoryModuleConfig;
import com.erp.module.inventory.controller.vo.StockDocVOs.BatchResult;
import com.erp.module.inventory.controller.vo.StockDocVOs.DocQuery;
import com.erp.module.inventory.controller.vo.StockDocVOs.DocRow;
import com.erp.module.inventory.controller.vo.StockDocVOs.Failure;
import com.erp.module.inventory.controller.vo.StockDocVOs.QuickCounts;
import com.erp.module.inventory.controller.vo.StockDocVOs.TransferDetail;
import com.erp.module.inventory.controller.vo.StockDocVOs.TransferLineResp;
import com.erp.module.inventory.controller.vo.StockDocVOs.TransferLineSave;
import com.erp.module.inventory.controller.vo.StockDocVOs.TransferSave;
import com.erp.module.inventory.dal.dataobject.LocationDO;
import com.erp.module.inventory.dal.dataobject.StockDO;
import com.erp.module.inventory.dal.dataobject.TransferDO;
import com.erp.module.inventory.dal.dataobject.TransferLineDO;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.mapper.StockMapper;
import com.erp.module.inventory.dal.mapper.TransferLineMapper;
import com.erp.module.inventory.dal.mapper.TransferMapper;
import com.erp.module.inventory.service.BatchService;
import com.erp.module.inventory.service.posting.PostingModels;
import com.erp.module.inventory.service.posting.PostingModels.PostLine;
import com.erp.module.inventory.service.posting.StockPostingService;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserDTO;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 调拨单（需求 08-05）：直接调拨，确认时同时调出、调入（同批次、同成本）。
 * 普通调拨、复检送检手工新建；检验调拨由品质判定后通过 InventoryDocApi 生成。
 */
@Service("invTransferService")
public class TransferService {

    public static final String BIZ_TYPE = "INV_TRANSFER";
    static final String PARAM_AUTO_CONFIRM = "inv.transfer.auto-confirm-inspection";

    static final Map<TransferType, String> TYPE_NAMES = Map.of(TransferType.NORMAL, "普通调拨", TransferType.INSPECTION, "检验调拨",
            TransferType.RECHECK, "复检送检");

    /** R01：调出、调入仓类型组合（1.1 节）；同仓库位间移库另由 R02 校验 */
    static boolean comboAllowed(TransferType t, WarehouseType from, WarehouseType to, boolean sameWarehouse) {
        return switch (t) {
            case NORMAL -> sameWarehouse || (from.available() && (to.available() || to == WarehouseType.NG));
            case INSPECTION -> sameWarehouse || (from == WarehouseType.QC && (to.available() || to == WarehouseType.NG))
                    || (from == WarehouseType.RTN && (to == WarehouseType.FG || to == WarehouseType.NG))
                    || (from == WarehouseType.NG && to.available());
            case RECHECK -> (from.available() || from == WarehouseType.NG) && to == WarehouseType.QC;
        };
    }

    private final TransferMapper docMapper;
    private final TransferLineMapper lineMapper;
    private final StockMapper stockMapper;
    private final StockPostingService postingService;
    private final BatchService batchService;
    private final DocSupport support;
    private final ParamApi paramApi;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate tx;

    public TransferService(TransferMapper docMapper, TransferLineMapper lineMapper, StockMapper stockMapper, StockPostingService postingService,
                           BatchService batchService, DocSupport support, ParamApi paramApi, DomainEventPublisher eventPublisher,
                           PlatformTransactionManager transactionManager) {
        this.docMapper = docMapper;
        this.lineMapper = lineMapper;
        this.stockMapper = stockMapper;
        this.postingService = postingService;
        this.batchService = batchService;
        this.support = support;
        this.paramApi = paramApi;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ==================== 业务生成（检验调拨） ====================

    /**
     * InventoryDocApi.createTransfer：检验调拨按判定结果拆单（R04）——合格/特采调入物料默认仓（或指定仓），不合格调入不良品仓。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createFromSource(TransferRequest req) {
        if (req.lines() == null || req.lines().isEmpty()) throw new BizException(InventoryErrorCodes.NO_LINES);
        if (req.fromWarehouseId() == null) throw BizException.of(InventoryErrorCodes.LINE_FIELD_REQUIRED, 1, "调出仓");
        TransferType type = req.transferType() == null ? TransferType.INSPECTION : req.transferType();
        WarehouseDO from = support.warehouse(req.fromWarehouseId());
        Map<Long, List<TransferRequest.Line>> byTarget = new LinkedHashMap<>();
        for (TransferRequest.Line l : req.lines()) {
            if (l.qty() == null || l.qty().signum() <= 0) continue;
            Long to;
            if (type == TransferType.INSPECTION && l.judgeResult() == JudgeResult.REJECTED) {
                to = support.warehouses().defaultOfType(WarehouseType.NG).map(WarehouseDO::getId)
                        .orElseThrow(() -> BizException.of(InventoryErrorCodes.NO_DEFAULT_WAREHOUSE, String.valueOf(l.materialId())));
            } else if (req.toWarehouseId() != null) {
                to = req.toWarehouseId();
            } else {
                to = support.warehouses().getDefaultWarehouse(l.materialId(), from.getWarehouseType() == WarehouseType.RTN ? WarehouseType.FG : null).id();
            }
            byTarget.computeIfAbsent(to, k -> new ArrayList<>()).add(l);
        }
        if (byTarget.isEmpty()) throw new BizException(InventoryErrorCodes.NO_LINES);
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, List<TransferRequest.Line>> e : byTarget.entrySet()) {
            WarehouseDO to = support.warehouse(e.getKey());
            checkCombo(type, from, to);
            TransferDO d = new TransferDO();
            d.setDocNo(support.nextNo(InventoryModuleConfig.CODE_TRANSFER));
            d.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
            d.setStatus(DocStatus.DRAFT);
            d.setTransferType(type);
            d.setFromWarehouseId(from.getId());
            d.setToWarehouseId(to.getId());
            d.setReason(req.reason());
            d.setInspectionId(req.inspectionId());
            if (req.source() != null) {
                d.setSourceType(req.source().sourceType());
                d.setSourceId(req.source().sourceId());
                d.setSourceNo(req.source().sourceNo());
            }
            d.setOwnerId(support.currentUser());
            docMapper.insert(d);
            int no = 0;
            for (TransferRequest.Line l : e.getValue()) {
                TransferLineDO line = new TransferLineDO();
                line.setTransferId(d.getId());
                line.setLineNo(++no);
                line.setMaterialId(l.materialId());
                line.setQty(l.qty());
                line.setBatchNo(trim(l.batchNo()));
                line.setFromLocationId(l.fromLocationId());
                line.setSerialNos(DocSupport.serialText(l.serialNos()));
                line.setJudgeResult(l.judgeResult());
                line.setSourceLineId(l.sourceLineId());
                lineMapper.insert(line);
            }
            ids.add(d.getId());
            // 调入仓未启用库位时不需要补充信息，参数允许则自动确认
            if (paramApi.getBool(PARAM_AUTO_CONFIRM) && !Boolean.TRUE.equals(to.getLocationEnabled())) confirm(d.getId(), null, null);
        }
        return ids;
    }

    /** 品质重新判定：作废该来源生成的未确认调拨单；已确认的阻止 */
    @Transactional(rollbackFor = Exception.class)
    public void cancelBySource(String sourceType, Long sourceId) {
        List<TransferDO> docs = docMapper.selectList(new LambdaQueryWrapper<TransferDO>().eq(TransferDO::getSourceType, sourceType)
                .eq(TransferDO::getSourceId, sourceId).ne(TransferDO::getStatus, DocStatus.VOIDED));
        for (TransferDO d : docs) {
            if (d.getStatus() == DocStatus.COMPLETED) throw BizException.of(InventoryErrorCodes.SOURCE_DOC_CONFIRMED, "调拨单", d.getDocNo(), "调拨");
        }
        for (TransferDO d : docs) fire(d, InvDocAction.VOID, "来源单据撤销");
    }

    // ==================== 手工（普通调拨、复检送检） ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(TransferSave req) {
        TransferType type = req.transferType() == null ? TransferType.NORMAL : req.transferType();
        if (type == TransferType.INSPECTION) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, "检验调拨", "手工新建");
        TransferDO d = new TransferDO();
        d.setDocNo(support.nextNo(InventoryModuleConfig.CODE_TRANSFER));
        d.setTransferType(type);
        d.setStatus(DocStatus.DRAFT);
        d.setOwnerId(support.currentUser());
        StockInService.LoginOrg.fill(d);
        fillManual(d, req);
        docMapper.insert(d);
        saveLines(d, req.lines());
        return d.getId();
    }

    private void fillManual(TransferDO d, TransferSave req) {
        if (!StringUtils.hasText(req.reason())) throw new BizException(InventoryErrorCodes.TRANSFER_REASON_REQUIRED);
        WarehouseDO from = support.warehouse(req.fromWarehouseId());
        WarehouseDO to = support.warehouse(req.toWarehouseId());
        support.warehouses().checkAccess(from.getId());
        support.warehouses().checkAccess(to.getId());
        checkCombo(d.getTransferType(), from, to);
        d.setFromWarehouseId(from.getId());
        d.setToWarehouseId(to.getId());
        d.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        DocSupport.checkDate(d.getDocDate(), null, "调拨");
        d.setReason(req.reason().trim());
        d.setRemark(trim(req.remark()));
    }

    private void saveLines(TransferDO d, List<TransferLineSave> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(InventoryErrorCodes.NO_LINES);
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(TransferLineSave::materialId).toList());
        lineMapper.deleteByDoc(d.getId());
        int no = 0;
        for (TransferLineSave l : lines) {
            no++;
            if (l.materialId() == null) throw BizException.of(InventoryErrorCodes.LINE_FIELD_REQUIRED, no, "物料");
            if (l.qty() == null || l.qty().signum() <= 0) throw BizException.of(InventoryErrorCodes.LINE_FIELD_REQUIRED, no, "数量");
            if (!materials.containsKey(l.materialId())) throw BizException.of(InventoryErrorCodes.MATERIAL_NOT_USABLE, String.valueOf(l.materialId()));
            TransferLineDO line = new TransferLineDO();
            line.setTransferId(d.getId());
            line.setLineNo(no);
            line.setMaterialId(l.materialId());
            line.setQty(l.qty());
            line.setBatchNo(trim(l.batchNo()));
            line.setFromLocationId(l.fromLocationId());
            line.setToLocationId(l.toLocationId());
            line.setSerialNos(DocSupport.serialText(l.serialNos()));
            line.setRemark(trim(l.remark()));
            lineMapper.insert(line);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TransferSave req) {
        TransferDO d = getOrThrow(id);
        checkAccess(d);
        if (d.getStatus() != DocStatus.DRAFT) throw new BizException(InventoryErrorCodes.DOC_NOT_EDITABLE);
        if (req.version() != null) d.setVersion(req.version());
        if (d.getTransferType() == TransferType.INSPECTION) {
            if (req.docDate() != null) {
                DocSupport.checkDate(req.docDate(), null, "调拨");
                d.setDocDate(req.docDate());
            }
            d.setRemark(trim(req.remark()));
            docMapper.updateByIdOrFail(d);
            supplement(d, req.lines());
            return;
        }
        fillManual(d, req);
        docMapper.updateByIdOrFail(d);
        saveLines(d, req.lines());
    }

    /** R05：检验调拨单只能补充库位、备注（按行 ID） */
    private void supplement(TransferDO d, List<TransferLineSave> lines) {
        if (lines == null) return;
        Map<Long, TransferLineDO> existing = lineMapper.selectByDoc(d.getId()).stream().collect(Collectors.toMap(TransferLineDO::getId, l -> l));
        for (TransferLineSave l : lines) {
            TransferLineDO line = l.id() == null ? null : existing.get(l.id());
            if (line == null) throw new BizException(InventoryErrorCodes.TRANSFER_INSPECTION_LOCKED);
            if ((l.materialId() != null && !l.materialId().equals(line.getMaterialId())) || (l.qty() != null && l.qty().compareTo(line.getQty()) != 0)
                    || (StringUtils.hasText(l.batchNo()) && !l.batchNo().trim().equals(line.getBatchNo()))) {
                throw new BizException(InventoryErrorCodes.TRANSFER_INSPECTION_LOCKED);
            }
            if (l.fromLocationId() != null) line.setFromLocationId(l.fromLocationId());
            line.setToLocationId(l.toLocationId());
            if (l.serialNos() != null && !l.serialNos().isEmpty()) line.setSerialNos(DocSupport.serialText(l.serialNos()));
            line.setRemark(trim(l.remark()));
            lineMapper.updateByIdOrFail(line);
        }
    }

    // ==================== 确认 ====================

    /** 确认调拨（R03）：一次过账调出 OUT + 调入 IN，同批次、同成本；特采批次打标（R04）；发布事件（R06、R07） */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, LocalDate docDate, List<TransferLineSave> lines) {
        TransferDO d = getOrThrow(id);
        checkAccess(d);
        if (d.getStatus() != DocStatus.DRAFT) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, d.getStatus().label(), "确认调拨");
        if (docDate != null) {
            DocSupport.checkDate(docDate, null, "调拨");
            d.setDocDate(docDate);
        }
        if (lines != null && !lines.isEmpty()) {
            if (d.getTransferType() == TransferType.INSPECTION) {
                supplement(d, lines);
            } else {
                saveLines(d, lines);
            }
        }
        WarehouseDO from = support.warehouse(d.getFromWarehouseId());
        WarehouseDO to = support.warehouse(d.getToWarehouseId());
        checkCombo(d.getTransferType(), from, to);
        List<TransferLineDO> ls = lineMapper.selectByDoc(id);
        if (ls.isEmpty()) throw new BizException(InventoryErrorCodes.NO_LINES);
        Map<Long, MaterialDTO> materials = support.materials(ls.stream().map(TransferLineDO::getMaterialId).toList());
        boolean same = from.getId().equals(to.getId());
        for (TransferLineDO l : ls) {
            if (same && (l.getFromLocationId() == null || Objects.equals(l.getFromLocationId(), l.getToLocationId()))) {
                throw new BizException(InventoryErrorCodes.TRANSFER_SAME_LOCATION);
            }
            MaterialDTO m = materials.get(l.getMaterialId());
            if (m != null && m.tracking() == Tracking.BATCH && !StringUtils.hasText(l.getBatchNo())) {
                throw BizException.of(InventoryErrorCodes.LINE_FIELD_REQUIRED, l.getLineNo(), "批次");
            }
        }
        // 报废送不良品仓、检验调拨、复检送检允许冻结/过期批次
        boolean frozenOk = d.getTransferType() != TransferType.NORMAL || to.getWarehouseType() == WarehouseType.NG;
        List<PostLine> post = new ArrayList<>();
        Map<Long, BigDecimal> costs = new HashMap<>();
        for (TransferLineDO l : ls) {
            BigDecimal cost = costs.computeIfAbsent(l.getMaterialId(), k -> support.refCost(k));
            List<String> sns = DocSupport.serials(l.getSerialNos());
            post.add(new PostLine(l.getId(), l.getSourceLineId(), StockDirection.OUT, l.getMaterialId(), from.getId(), l.getFromLocationId(),
                    l.getBatchNo(), l.getQty(), cost, sns, frozenOk, null));
            post.add(new PostLine(l.getId(), l.getSourceLineId(), StockDirection.IN, l.getMaterialId(), to.getId(), l.getToLocationId(),
                    l.getBatchNo(), l.getQty(), cost, sns, false, null));
        }
        postingService.post(new PostingModels.PostCommand(PostingModels.DOC_TRANSFER, d.getTransferType().name(), d.getId(), d.getDocNo(),
                d.getSourceType(), d.getSourceId(), d.getSourceNo(), d.getDocDate(), post, false, false, null));
        for (TransferLineDO l : ls) {
            if (l.getJudgeResult() == JudgeResult.CONCESSION && StringUtils.hasText(l.getBatchNo())) batchService.markConcession(l.getMaterialId(), l.getBatchNo());
        }
        d.setConfirmedBy(SecurityUtils.getLoginUserIdOrNull());
        d.setConfirmedAt(LocalDateTime.now());
        fire(d, InvDocAction.CONFIRM, null);
        SourceRef src = new SourceRef(d.getSourceType(), d.getSourceId(), d.getSourceNo());
        eventPublisher.publish(new TransferConfirmedEvent(d.getId(), d.getDocNo(), d.getTransferType(), src, d.getInspectionId(), from.getId(), to.getId(),
                ls.stream().map(l -> new TransferConfirmedEvent.Line(l.getSourceLineId(), l.getMaterialId(), l.getBatchNo(), l.getQty(), l.getJudgeResult())).toList()));
        if (d.getTransferType() == TransferType.RECHECK) {
            eventPublisher.publish(new StockDocEvent(PostingModels.DOC_TRANSFER, StockDocEvent.Kind.RECHECK_REQUESTED, d.getId(), d.getDocNo(), src, d.getReason()));
        }
    }

    public BatchResult batchConfirm(List<Long> ids) {
        int ok = 0;
        List<Failure> failures = new ArrayList<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            TransferDO d = docMapper.selectById(id);
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
    public void voidDoc(Long id, String reason) {
        TransferDO d = getOrThrow(id);
        checkAccess(d);
        if (d.getTransferType() == TransferType.INSPECTION) throw new BizException(InventoryErrorCodes.TRANSFER_INSPECTION_VOID);
        fire(d, InvDocAction.VOID, reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TransferDO d = getOrThrow(id);
        checkAccess(d);
        if (d.getTransferType() == TransferType.INSPECTION) throw new BizException(InventoryErrorCodes.TRANSFER_INSPECTION_VOID);
        if (d.getStatus() != DocStatus.DRAFT) throw new BizException(InventoryErrorCodes.DOC_NOT_EDITABLE);
        lineMapper.deleteByDoc(id);
        docMapper.deleteById(id);
    }

    private void fire(TransferDO d, InvDocAction action, String reason) {
        DocStatus old = d.getStatus();
        d.setStatus(InvDocAction.MACHINE.fire(old, action));
        docMapper.updateByIdOrFail(d);
        support.log(BIZ_TYPE, d.getId(), d.getDocNo(), action, action == InvDocAction.CONFIRM ? "确认调拨" : null, old, d.getStatus(), reason);
    }

    private void checkCombo(TransferType type, WarehouseDO from, WarehouseDO to) {
        boolean same = from.getId().equals(to.getId());
        if (same && !Boolean.TRUE.equals(from.getLocationEnabled())) throw new BizException(InventoryErrorCodes.TRANSFER_SAME_LOCATION);
        if (!comboAllowed(type, from.getWarehouseType(), to.getWarehouseType(), same)) {
            throw BizException.of(InventoryErrorCodes.TRANSFER_NOT_ALLOWED, from.getName(), to.getName());
        }
    }

    /** 调出仓或调入仓任一有权限即可操作 */
    private void checkAccess(TransferDO d) {
        Set<Long> allowed = support.warehouses().accessibleIds();
        if (allowed == null || allowed.contains(d.getFromWarehouseId()) || allowed.contains(d.getToWarehouseId())) return;
        support.warehouses().checkAccess(d.getFromWarehouseId());
    }

    public TransferDO getOrThrow(Long id) {
        TransferDO d = id == null ? null : docMapper.selectById(id);
        if (d == null) throw new BizException(InventoryErrorCodes.DOC_NOT_EXISTS);
        return d;
    }

    // ==================== 查询 ====================

    public PageResult<DocRow> page(DocQuery q) {
        LambdaQueryWrapper<TransferDO> w = query(q);
        if (w == null) return PageResult.empty();
        PageResult<TransferDO> page = docMapper.selectPage(q, w);
        return new PageResult<>(rows(page.list()), page.total());
    }

    public List<DocRow> listForExport(DocQuery q, int limit) {
        LambdaQueryWrapper<TransferDO> w = query(q);
        return w == null ? List.of() : rows(docMapper.selectList(w.last("LIMIT " + limit)));
    }

    public QuickCounts quickCounts() {
        Set<Long> allowed = support.warehouses().accessibleIds();
        if (allowed != null && allowed.isEmpty()) return new QuickCounts(0, 0);
        long todo = docMapper.selectCount(scope(new LambdaQueryWrapper<TransferDO>(), allowed).eq(TransferDO::getStatus, DocStatus.DRAFT));
        long today = docMapper.selectCount(scope(new LambdaQueryWrapper<TransferDO>(), allowed).eq(TransferDO::getStatus, DocStatus.COMPLETED)
                .ge(TransferDO::getConfirmedAt, LocalDate.now().atStartOfDay()));
        return new QuickCounts(todo, today);
    }

    private static LambdaQueryWrapper<TransferDO> scope(LambdaQueryWrapper<TransferDO> w, Set<Long> allowed) {
        if (allowed != null) w.and(x -> x.in(TransferDO::getFromWarehouseId, allowed).or().in(TransferDO::getToWarehouseId, allowed));
        return w;
    }

    private LambdaQueryWrapper<TransferDO> query(DocQuery q) {
        Set<Long> allowed = support.warehouses().accessibleIds();
        if (allowed != null && allowed.isEmpty()) return null;
        LambdaQueryWrapper<TransferDO> w = scope(new LambdaQueryWrapper<>(), allowed)
                .likeRight(StringUtils.hasText(q.getDocNo()), TransferDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getWarehouseId() != null, TransferDO::getFromWarehouseId, q.getWarehouseId())
                .eq(q.getToWarehouseId() != null, TransferDO::getToWarehouseId, q.getToWarehouseId())
                .likeRight(StringUtils.hasText(q.getSourceNo()), TransferDO::getSourceNo, q.getSourceNo() == null ? null : q.getSourceNo().trim())
                .ge(q.getDateFrom() != null, TransferDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, TransferDO::getDocDate, q.getDateTo());
        if (StringUtils.hasText(q.getIds())) w.in(TransferDO::getId, StockInService.ids(q.getIds()));
        if (StringUtils.hasText(q.getTypes())) w.in(TransferDO::getTransferType, Arrays.stream(q.getTypes().split(",")).map(String::trim).map(TransferType::valueOf).toList());
        if ("TODO".equals(q.getQuick())) {
            w.eq(TransferDO::getStatus, DocStatus.DRAFT);
        } else if ("TODAY".equals(q.getQuick())) {
            w.eq(TransferDO::getStatus, DocStatus.COMPLETED).ge(TransferDO::getConfirmedAt, LocalDate.now().atStartOfDay());
        } else if (StringUtils.hasText(q.getStatuses())) {
            w.in(TransferDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        if (q.getMaterialId() != null) {
            w.inSql(TransferDO::getId, "SELECT transfer_id FROM inv_transfer_line WHERE deleted = 0 AND material_id = " + q.getMaterialId().longValue());
        }
        return w.orderByDesc(TransferDO::getDocDate).orderByDesc(TransferDO::getId);
    }

    private List<DocRow> rows(List<TransferDO> docs) {
        if (docs.isEmpty()) return List.of();
        List<TransferLineDO> lines = lineMapper.selectByDocs(docs.stream().map(TransferDO::getId).toList());
        Map<Long, List<TransferLineDO>> byDoc = lines.stream().collect(Collectors.groupingBy(TransferLineDO::getTransferId));
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(TransferLineDO::getMaterialId).toList());
        Set<Long> whIds = new HashSet<>();
        docs.forEach(d -> {
            whIds.add(d.getFromWarehouseId());
            whIds.add(d.getToWarehouseId());
        });
        Map<Long, WarehouseDO> whs = support.warehouses().byIds(whIds);
        Set<Long> userIds = new HashSet<>();
        docs.forEach(d -> {
            userIds.add(d.getConfirmedBy());
            userIds.add(d.getCreatedBy());
        });
        Map<Long, UserDTO> users = support.users(userIds);
        return docs.stream().map(d -> {
            List<TransferLineDO> ls = byDoc.getOrDefault(d.getId(), List.of());
            WarehouseDO f = whs.get(d.getFromWarehouseId());
            WarehouseDO t = whs.get(d.getToWarehouseId());
            Set<String> uoms = ls.stream().map(l -> materials.containsKey(l.getMaterialId()) ? materials.get(l.getMaterialId()).baseUom() : "").collect(Collectors.toSet());
            BigDecimal total = uoms.size() == 1 ? ls.stream().map(TransferLineDO::getQty).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
            return new DocRow(d.getId(), d.getDocNo(), PostingModels.DOC_TRANSFER, d.getTransferType().name(), d.getFromWarehouseId(),
                    f == null ? null : f.getName(), f == null ? null : f.getWarehouseType(), d.getToWarehouseId(), t == null ? null : t.getName(),
                    t == null ? null : t.getWarehouseType(), d.getSourceType(), d.getSourceId(), d.getSourceNo(), null,
                    DocSupport.summary(null, materials, ls.stream().map(TransferLineDO::getMaterialId).toList()), ls.size(), total, null, d.getDocDate(),
                    d.getStatus().name(), d.getTransferType() != TransferType.INSPECTION, DocSupport.name(users, d.getConfirmedBy()), d.getConfirmedAt(),
                    DocSupport.name(users, d.getCreatedBy()));
        }).toList();
    }

    public TransferDetail detail(Long id) {
        TransferDO d = getOrThrow(id);
        checkAccess(d);
        List<TransferLineDO> lines = lineMapper.selectByDoc(id);
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(TransferLineDO::getMaterialId).toList());
        Set<Long> locIds = new HashSet<>();
        lines.forEach(l -> {
            if (l.getFromLocationId() != null) locIds.add(l.getFromLocationId());
            if (l.getToLocationId() != null) locIds.add(l.getToLocationId());
        });
        Map<Long, LocationDO> locs = support.warehouses().locationsByIds(locIds);
        WarehouseDO from = support.warehouse(d.getFromWarehouseId());
        WarehouseDO to = support.warehouse(d.getToWarehouseId());
        Map<Long, UserDTO> users = support.users(List.of(nz(d.getConfirmedBy()), nz(d.getCreatedBy())));
        Map<String, BigDecimal> onHand = new HashMap<>();
        if (!lines.isEmpty()) {
            stockMapper.selectList(new LambdaQueryWrapper<StockDO>().eq(StockDO::getWarehouseId, from.getId())
                            .in(StockDO::getMaterialId, materials.keySet().isEmpty() ? List.of(0L) : materials.keySet()))
                    .forEach(s -> onHand.merge(s.getMaterialId() + "|" + s.getBatchNo(), s.getQty(), BigDecimal::add));
        }
        List<TransferLineResp> resp = lines.stream().map(l -> {
            MaterialDTO m = materials.get(l.getMaterialId());
            return new TransferLineResp(l.getId(), l.getLineNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.spec(), m == null ? null : m.baseUom(), m == null || m.tracking() == null ? "NONE" : m.tracking().name(), l.getQty(),
                    onHand.getOrDefault(l.getMaterialId() + "|" + (l.getBatchNo() == null ? "" : l.getBatchNo()), BigDecimal.ZERO), l.getBatchNo(),
                    l.getFromLocationId(), code(locs, l.getFromLocationId()), l.getToLocationId(), code(locs, l.getToLocationId()),
                    DocSupport.serials(l.getSerialNos()), l.getJudgeResult(), l.getRemark());
        }).toList();
        return new TransferDetail(d.getId(), d.getDocNo(), d.getTransferType(), from.getId(), from.getName(), from.getWarehouseType(),
                Boolean.TRUE.equals(from.getLocationEnabled()), to.getId(), to.getName(), to.getWarehouseType(), Boolean.TRUE.equals(to.getLocationEnabled()),
                d.getDocDate(), d.getStatus().name(), d.getReason(), d.getInspectionId(), d.getSourceType(), d.getSourceId(), d.getSourceNo(), d.getRemark(),
                d.getConfirmedBy(), DocSupport.name(users, d.getConfirmedBy()), d.getConfirmedAt(), DocSupport.name(users, d.getCreatedBy()),
                d.getCreatedAt(), d.getVersion(), resp);
    }

    private static String code(Map<Long, LocationDO> locs, Long id) {
        return id == null || !locs.containsKey(id) ? null : locs.get(id).getCode();
    }

    public Map<String, Object> printData(Long id) {
        TransferDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("docDate", d.docDate());
        data.put("typeName", TYPE_NAMES.get(d.transferType()));
        data.put("warehouseName", d.fromWarehouseName());
        data.put("toWarehouseName", d.toWarehouseName());
        data.put("sourceNo", Objects.toString(d.sourceNo(), ""));
        data.put("partnerName", "");
        data.put("reasonName", Objects.toString(d.reason(), ""));
        data.put("remark", Objects.toString(d.remark(), ""));
        data.put("statusName", DocStatus.valueOf(d.status()).label());
        data.put("confirmedByName", Objects.toString(d.confirmedByName(), ""));
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (TransferLineResp l : d.lines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.lineNo());
            m.put("code", l.materialCode());
            m.put("name", l.materialName());
            m.put("spec", Objects.toString(l.materialSpec(), ""));
            m.put("uom", l.baseUom());
            m.put("qty", l.qty());
            m.put("batchNo", Objects.toString(l.batchNo(), ""));
            m.put("locationCode", Objects.toString(l.fromLocationCode(), "") + (l.toLocationCode() == null ? "" : " → " + l.toLocationCode()));
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
