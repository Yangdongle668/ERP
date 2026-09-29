package com.erp.module.production.service.material;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.StockOutConfirmedEvent;
import com.erp.module.inventory.api.doc.StockOutRequest;
import com.erp.module.inventory.api.doc.StockOutType;
import com.erp.module.inventory.api.stock.InventoryQueryApi;
import com.erp.module.inventory.api.warehouse.WarehouseDTO;
import com.erp.module.production.api.ProductionErrorCodes;
import com.erp.module.production.config.ProductionModuleConfig;
import com.erp.module.production.controller.vo.MaterialDocVOs.ByKitReq;
import com.erp.module.production.controller.vo.MaterialDocVOs.CreateResult;
import com.erp.module.production.controller.vo.MaterialDocVOs.IssueCandidate;
import com.erp.module.production.controller.vo.MaterialDocVOs.IssueDetail;
import com.erp.module.production.controller.vo.MaterialDocVOs.IssueLineResp;
import com.erp.module.production.controller.vo.MaterialDocVOs.IssueLineSave;
import com.erp.module.production.controller.vo.MaterialDocVOs.IssueQuery;
import com.erp.module.production.controller.vo.MaterialDocVOs.IssueRow;
import com.erp.module.production.controller.vo.MaterialDocVOs.IssueSave;
import com.erp.module.production.controller.vo.MaterialDocVOs.OverReq;
import com.erp.module.production.controller.vo.CommonVOs.DocResult;
import com.erp.module.production.dal.dataobject.MfgIssueDO;
import com.erp.module.production.dal.dataobject.MfgIssueLineDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderMaterialDO;
import com.erp.module.production.dal.dataobject.MfgReportDO;
import com.erp.module.production.dal.mapper.MfgIssueLineMapper;
import com.erp.module.production.dal.mapper.MfgIssueMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMaterialMapper;
import com.erp.module.production.dal.mapper.MfgReportMapper;
import com.erp.module.production.service.MfgAction;
import com.erp.module.production.service.MfgStateMachines;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.order.MaterialPlanner;
import com.erp.module.production.service.order.OrderProgressService;
import com.erp.module.production.service.trace.TraceRecorder;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 领料单（需求 09-03）：正常领料、超领（审批 MFG_ISSUE_OVER）、倒冲（报工审核时系统生成）。
 * 提交后生成仓库“生产领料出库单”（一张领料单对应一个发料仓、一张出库单），出库确认回写已领数量并写追溯。
 */
@Service("mfgIssueService")
public class IssueService {

    public static final String BIZ_TYPE = ProductionModuleConfig.ISSUE;
    public static final String SOURCE_TYPE = "MFG_ISSUE";
    static final Map<String, String> TYPE_NAMES = Map.of("NORMAL", "正常领料", "OVER", "超领", "BACKFLUSH", "倒冲");

    /** 倒冲需求：用料行 + 数量（基本单位） */
    public record BackflushNeed(MfgProdOrderMaterialDO material, BigDecimal qty) {
    }

    private final MfgIssueMapper mapper;
    private final MfgIssueLineMapper lineMapper;
    private final MfgProdOrderMapper orderMapper;
    private final MfgProdOrderMaterialMapper materialMapper;
    private final MfgReportMapper reportMapper;
    private final OrderProgressService progress;
    private final MfgSupport support;
    private final InventoryDocApi inventoryDocApi;
    private final InventoryQueryApi inventoryQueryApi;
    private final WorkflowApi workflowApi;
    private final TraceRecorder trace;

    public IssueService(MfgIssueMapper mapper, MfgIssueLineMapper lineMapper, MfgProdOrderMapper orderMapper, MfgProdOrderMaterialMapper materialMapper,
                        MfgReportMapper reportMapper, OrderProgressService progress, MfgSupport support, InventoryDocApi inventoryDocApi,
                        InventoryQueryApi inventoryQueryApi, WorkflowApi workflowApi, TraceRecorder trace) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.reportMapper = reportMapper;
        this.progress = progress;
        this.support = support;
        this.inventoryDocApi = inventoryDocApi;
        this.inventoryQueryApi = inventoryQueryApi;
        this.workflowApi = workflowApi;
        this.trace = trace;
    }

    public MfgIssueDO getOrThrow(Long id) {
        MfgIssueDO i = id == null ? null : mapper.selectById(id);
        if (i == null) throw new BizException(ProductionErrorCodes.ISSUE_NOT_EXISTS);
        return i;
    }

    // ==================== 查询 ====================

    public PageResult<IssueRow> page(IssueQuery q) {
        LambdaQueryWrapper<MfgIssueDO> w = new LambdaQueryWrapper<MfgIssueDO>().eq(MfgIssueDO::getDeleted, false);
        if (StringUtils.hasText(q.getDocNo())) w.likeRight(MfgIssueDO::getDocNo, q.getDocNo().trim());
        if (q.getProdOrderId() != null) w.eq(MfgIssueDO::getProdOrderId, q.getProdOrderId());
        if (StringUtils.hasText(q.getProdOrderNo())) w.likeRight(MfgIssueDO::getSourceNo, q.getProdOrderNo().trim());
        if (StringUtils.hasText(q.getIssueType())) w.eq(MfgIssueDO::getIssueType, q.getIssueType());
        if (StringUtils.hasText(q.getStatuses())) w.in(MfgIssueDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(DocStatus::valueOf).toList());
        if (q.getWarehouseId() != null) w.eq(MfgIssueDO::getWarehouseId, q.getWarehouseId());
        if (q.getMaterialId() != null) {
            List<Long> ids = lineMapper.selectList(new LambdaQueryWrapper<MfgIssueLineDO>().eq(MfgIssueLineDO::getMaterialId, q.getMaterialId()))
                    .stream().map(MfgIssueLineDO::getIssueId).distinct().toList();
            if (ids.isEmpty()) return new PageResult<>(List.of(), 0L);
            w.in(MfgIssueDO::getId, ids);
        }
        if (q.getDateFrom() != null) w.ge(MfgIssueDO::getDocDate, q.getDateFrom());
        if (q.getDateTo() != null) w.le(MfgIssueDO::getDocDate, q.getDateTo());
        w.orderByDesc(MfgIssueDO::getId);
        IPage<MfgIssueDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        List<MfgIssueDO> list = p.getRecords();
        Map<Long, MfgProdOrderDO> orders = orders(list.stream().map(MfgIssueDO::getProdOrderId).toList());
        Map<Long, MaterialDTO> ms = support.materials(orders.values().stream().map(MfgProdOrderDO::getMaterialId).toList());
        Map<Long, WarehouseDTO> whs = support.warehouses(list.stream().map(MfgIssueDO::getWarehouseId).toList());
        Map<Long, Long> counts = lineMapper.selectByParents(list.stream().map(MfgIssueDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(MfgIssueLineDO::getIssueId, Collectors.counting()));
        Map<Long, UserDTO> users = support.users(list.stream().map(MfgIssueDO::getOwnerId).toList());
        return new PageResult<>(list.stream().map(i -> {
            MfgProdOrderDO o = orders.get(i.getProdOrderId());
            MaterialDTO m = o == null ? null : ms.get(o.getMaterialId());
            WarehouseDTO wh = whs.get(i.getWarehouseId());
            return new IssueRow(i.getId(), i.getDocNo(), i.getIssueType(), i.getProdOrderId(), o == null ? null : o.getDocNo(), m == null ? null : m.code(),
                    m == null ? null : m.name(), i.getWarehouseId(), wh == null ? null : wh.name(), counts.getOrDefault(i.getId(), 0L).intValue(),
                    i.getStatus().name(), i.getStockOutNos(), i.getOverReason(), MfgSupport.name(users, i.getOwnerId()), i.getDocDate());
        }).toList(), p.getTotal());
    }

    private Map<Long, MfgProdOrderDO> orders(List<Long> ids) {
        List<Long> set = ids.stream().filter(Objects::nonNull).distinct().toList();
        return set.isEmpty() ? Map.of() : orderMapper.selectBatchIds(set).stream().collect(Collectors.toMap(MfgProdOrderDO::getId, Function.identity()));
    }

    public IssueDetail detail(Long id) {
        MfgIssueDO i = getOrThrow(id);
        DataScopes.check(i.getOrgId(), i.getDeptId(), i.getOwnerId(), "领料单");
        MfgProdOrderDO o = progress.getOrThrow(i.getProdOrderId());
        MaterialDTO product = support.material(o.getMaterialId());
        List<MfgIssueLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, MfgProdOrderMaterialDO> mats = materialMapper.selectBatchIds(lines.isEmpty() ? List.of(0L) : lines.stream().map(MfgIssueLineDO::getMaterialLineId).toList())
                .stream().collect(Collectors.toMap(MfgProdOrderMaterialDO::getId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(MfgIssueLineDO::getMaterialId).toList());
        List<IssueLineResp> resp = lines.stream().map(l -> {
            MaterialDTO c = ms.get(l.getMaterialId());
            MfgProdOrderMaterialDO m = mats.get(l.getMaterialLineId());
            BigDecimal avail = i.getWarehouseId() == null ? null : inventoryQueryApi.getAvailableQty(l.getMaterialId(), i.getWarehouseId());
            return new IssueLineResp(l.getId(), l.getLineNo(), l.getMaterialLineId(), l.getMaterialId(), c == null ? null : c.code(), c == null ? null : c.name(),
                    c == null ? null : c.spec(), c == null ? null : c.baseUom(), m == null ? null : m.getRequiredQty(), m == null ? null : m.getIssuedQty(),
                    m == null ? null : MaterialPlanner.openQty(m), avail, l.getRequestQty(), l.getIssuedQty(), l.getRemark());
        }).toList();
        MfgReportDO report = i.getReportId() == null ? null : reportMapper.selectById(i.getReportId());
        return new IssueDetail(i.getId(), i.getDocNo(), i.getIssueType(), i.getStatus().name(), i.getDocDate(), o.getId(), o.getDocNo(), o.getProdStatus(),
                product.id(), product.code(), product.name(), i.getWarehouseId(), support.warehouseName(i.getWarehouseId()), i.getKitQty(), i.getOverReason(),
                i.getOverRemark(), i.getReportId(), report == null ? null : report.getDocNo(), i.getStockOutIds(), i.getStockOutNos(), i.getRemark(),
                i.getOwnerId(), support.userName(i.getOwnerId()), i.getSubmittedAt(), i.getCreatedAt(), i.getVersion(), resp);
    }

    /**
     * 新建领料的候选行（3.2）：发料方式为领料、未领 > 0 的用料行；按套数时申请数量 = min(套数 × 单位用量 × (1+损耗), 未领)。
     */
    public List<IssueCandidate> candidates(Long prodOrderId, BigDecimal kitQty) {
        MfgProdOrderDO o = progress.getOrThrow(prodOrderId);
        List<MfgProdOrderMaterialDO> mats = materialMapper.selectByParent(prodOrderId);
        Map<Long, BigDecimal> pending = pendingRequests(prodOrderId, null);
        Map<Long, MaterialDTO> ms = support.materials(mats.stream().map(MfgProdOrderMaterialDO::getComponentId).toList());
        List<IssueCandidate> out = new ArrayList<>();
        for (MfgProdOrderMaterialDO m : mats) {
            if (!"PICK".equals(m.getIssueMethod())) continue;
            BigDecimal pend = pending.getOrDefault(m.getId(), BigDecimal.ZERO);
            BigDecimal open = MfgSupport.max0(MaterialPlanner.openQty(m).subtract(pend));
            if (open.signum() <= 0) continue;
            MaterialDTO c = ms.get(m.getComponentId());
            BigDecimal request = open;
            if (kitQty != null && kitQty.signum() > 0) {
                request = support.roundUp(kitQty.multiply(m.getQtyPer()).multiply(BigDecimal.ONE.add(MfgSupport.nz(m.getScrapRate()))),
                        c == null ? null : c.baseUom()).min(open);
            }
            WarehouseDTO wh = defaultWarehouse(m.getComponentId());
            BigDecimal avail = wh == null ? inventoryQueryApi.getAvailableQty(m.getComponentId()) : inventoryQueryApi.getAvailableQty(m.getComponentId(), wh.id());
            out.add(new IssueCandidate(m.getId(), m.getComponentId(), c == null ? null : c.code(), c == null ? null : c.name(), c == null ? null : c.spec(),
                    c == null ? null : c.baseUom(), m.getIssueMethod(), m.getRequiredQty(), m.getIssuedQty(), MaterialPlanner.openQty(m), pend,
                    wh == null ? null : wh.id(), wh == null ? null : wh.name(), avail, request));
        }
        return out;
    }

    private WarehouseDTO defaultWarehouse(Long materialId) {
        try {
            return support.warehouseApi().getDefaultWarehouse(materialId, null);
        } catch (BizException e) {
            return null;
        }
    }

    /** 其他已提交未发完的正常领料（含待审批）占用的申请数量，键：用料行 */
    private Map<Long, BigDecimal> pendingRequests(Long prodOrderId, Long excludeIssueId) {
        List<MfgIssueDO> issues = mapper.selectList(new LambdaQueryWrapper<MfgIssueDO>().eq(MfgIssueDO::getProdOrderId, prodOrderId)
                .ne(MfgIssueDO::getIssueType, "BACKFLUSH").in(MfgIssueDO::getStatus, List.of(DocStatus.PENDING_APPROVAL, DocStatus.APPROVED))
                .ne(excludeIssueId != null, MfgIssueDO::getId, excludeIssueId));
        Map<Long, BigDecimal> map = new HashMap<>();
        if (issues.isEmpty()) return map;
        Map<Long, String> types = issues.stream().collect(Collectors.toMap(MfgIssueDO::getId, MfgIssueDO::getIssueType));
        for (MfgIssueLineDO l : lineMapper.selectByParents(types.keySet())) {
            if ("OVER".equals(types.get(l.getIssueId()))) continue;
            map.merge(l.getMaterialLineId(), MfgSupport.max0(l.getRequestQty().subtract(l.getIssuedQty())), BigDecimal::add);
        }
        return map;
    }

    // ==================== 新建 / 修改 ====================

    /** 新建正常领料：按发料仓拆分为多张 */
    @Transactional(rollbackFor = Exception.class)
    public CreateResult create(IssueSave req) {
        MfgProdOrderDO o = progress.getOrThrow(req.prodOrderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        OrderProgressService.requireRunning(o);
        List<IssueLineSave> lines = req.lines() == null ? List.of() : req.lines().stream().filter(l -> l.requestQty() != null && l.requestQty().signum() > 0).toList();
        if (lines.isEmpty()) throw new BizException(ProductionErrorCodes.DOC_NO_LINES);
        Map<Long, MfgProdOrderMaterialDO> mats = materialMapper.selectByParent(o.getId()).stream()
                .collect(Collectors.toMap(MfgProdOrderMaterialDO::getId, Function.identity()));
        Map<Long, List<IssueLineSave>> byWarehouse = new LinkedHashMap<>();
        int no = 0;
        for (IssueLineSave l : lines) {
            no++;
            MfgProdOrderMaterialDO m = mats.get(l.materialLineId());
            if (m == null) throw BizException.of(ProductionErrorCodes.ISSUE_LINE_INVALID, no);
            Long wh = l.warehouseId() != null ? l.warehouseId() : support.warehouseApi().getDefaultWarehouse(m.getComponentId(), null).id();
            byWarehouse.computeIfAbsent(wh, k -> new ArrayList<>()).add(l);
        }
        List<Long> ids = new ArrayList<>();
        List<String> nos = new ArrayList<>();
        for (Map.Entry<Long, List<IssueLineSave>> e : byWarehouse.entrySet()) {
            MfgIssueDO i = newIssue(o, "NORMAL", e.getKey());
            i.setKitQty(req.kitQty());
            i.setRemark(MfgSupport.trim(req.remark()));
            mapper.insert(i);
            saveLines(i, e.getValue(), mats);
            ids.add(i.getId());
            nos.add(i.getDocNo());
        }
        return new CreateResult(ids, nos, List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, IssueSave req) {
        MfgIssueDO i = getOrThrow(id);
        DataScopes.check(i.getOrgId(), i.getDeptId(), i.getOwnerId(), "领料单");
        MfgSupport.requireDraft(i);
        MfgProdOrderDO o = progress.getOrThrow(i.getProdOrderId());
        if (req.version() != null) i.setVersion(req.version());
        i.setRemark(MfgSupport.trim(req.remark()));
        if (req.kitQty() != null) i.setKitQty(req.kitQty());
        mapper.updateByIdOrFail(i);
        List<IssueLineSave> lines = req.lines() == null ? List.of() : req.lines().stream().filter(l -> l.requestQty() != null && l.requestQty().signum() > 0).toList();
        if (lines.isEmpty()) throw new BizException(ProductionErrorCodes.DOC_NO_LINES);
        lineMapper.deleteByParent(id);
        saveLines(i, lines, materialMapper.selectByParent(o.getId()).stream().collect(Collectors.toMap(MfgProdOrderMaterialDO::getId, Function.identity())));
    }

    private MfgIssueDO newIssue(MfgProdOrderDO o, String type, Long warehouseId) {
        MfgIssueDO i = new MfgIssueDO();
        i.setDocNo(support.nextNo(BIZ_TYPE));
        i.setDocDate(LocalDate.now());
        i.setStatus(DocStatus.DRAFT);
        i.setIssueType(type);
        i.setProdOrderId(o.getId());
        i.setSourceType(ProductionModuleConfig.PROD_ORDER);
        i.setSourceId(o.getId());
        i.setSourceNo(o.getDocNo());
        i.setWarehouseId(warehouseId);
        support.fillOwner(i, null, o.getDeptId());
        i.setDeptId(o.getDeptId());
        return i;
    }

    private void saveLines(MfgIssueDO i, List<IssueLineSave> lines, Map<Long, MfgProdOrderMaterialDO> mats) {
        int no = 0;
        for (IssueLineSave l : lines) {
            no++;
            MfgProdOrderMaterialDO m = mats.get(l.materialLineId());
            if (m == null) throw BizException.of(ProductionErrorCodes.ISSUE_LINE_INVALID, no);
            MfgIssueLineDO line = new MfgIssueLineDO();
            line.setIssueId(i.getId());
            line.setLineNo(no);
            line.setMaterialLineId(m.getId());
            line.setMaterialId(m.getComponentId());
            line.setWarehouseId(i.getWarehouseId());
            line.setRequestQty(l.requestQty());
            line.setIssuedQty(BigDecimal.ZERO);
            line.setRemark(MfgSupport.trim(l.remark()));
            lineMapper.insert(line);
        }
    }

    /** 批量按套数领料：每张订单按领料行生成（按发料仓拆分），可直接提交 */
    @Transactional(rollbackFor = Exception.class)
    public CreateResult byKit(ByKitReq req) {
        List<Long> ids = new ArrayList<>();
        List<String> nos = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Long orderId : req.prodOrderIds()) {
            MfgProdOrderDO o = progress.getOrThrow(orderId);
            List<IssueLineSave> lines = candidates(orderId, req.kitQty()).stream().filter(c -> c.requestQty().signum() > 0)
                    .map(c -> new IssueLineSave(c.materialLineId(), c.requestQty(), c.warehouseId(), null)).toList();
            if (lines.isEmpty()) {
                warnings.add(o.getDocNo() + "：没有需要领料的物料");
                continue;
            }
            CreateResult r = create(new IssueSave(orderId, req.kitQty(), null, lines, null));
            ids.addAll(r.ids());
            nos.addAll(r.docNos());
            if (Boolean.TRUE.equals(req.submit())) for (Long id : r.ids()) submit(id);
        }
        if (ids.isEmpty() && warnings.isEmpty()) throw new BizException(ProductionErrorCodes.ISSUE_NOTHING);
        return new CreateResult(ids, nos, warnings);
    }

    /** 超领单（3.3）：一行，原因必填，提交按审批流 */
    @Transactional(rollbackFor = Exception.class)
    public CreateResult over(OverReq req) {
        MfgProdOrderDO o = progress.getOrThrow(req.prodOrderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        OrderProgressService.requireRunning(o);
        if (req.qty() == null || req.qty().signum() <= 0) throw new BizException(ProductionErrorCodes.ISSUE_OVER_QTY);
        if (!StringUtils.hasText(req.overReason())) throw new BizException(ProductionErrorCodes.ISSUE_OVER_REASON);
        MfgProdOrderMaterialDO m = materialMapper.selectById(req.materialLineId());
        if (m == null || !m.getProdOrderId().equals(o.getId())) throw BizException.of(ProductionErrorCodes.ISSUE_LINE_INVALID, 1);
        Long wh = req.warehouseId() != null ? req.warehouseId() : support.warehouseApi().getDefaultWarehouse(m.getComponentId(), null).id();
        MfgIssueDO i = newIssue(o, "OVER", wh);
        i.setOverReason(req.overReason());
        i.setOverRemark(MfgSupport.trim(req.overRemark()));
        mapper.insert(i);
        saveLines(i, List.of(new IssueLineSave(m.getId(), req.qty(), wh, null)), Map.of(m.getId(), m));
        if (Boolean.TRUE.equals(req.submit())) submit(i.getId());
        return new CreateResult(List.of(i.getId()), List.of(i.getDocNo()), List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MfgIssueDO i = getOrThrow(id);
        DataScopes.check(i.getOrgId(), i.getDeptId(), i.getOwnerId(), "领料单");
        MfgSupport.requireDraft(i);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
    }

    // ==================== 提交 / 撤回 ====================

    /** 提交（R01、R02）：正常领料直接生成出库单；超领走审批流，通过后生成出库单 */
    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        MfgIssueDO i = getOrThrow(id);
        DataScopes.check(i.getOrgId(), i.getDeptId(), i.getOwnerId(), "领料单");
        MfgSupport.requireDraft(i);
        MfgProdOrderDO o = progress.getOrThrow(i.getProdOrderId());
        OrderProgressService.requireRunning(o);
        List<MfgIssueLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(ProductionErrorCodes.DOC_NO_LINES);
        Map<Long, MfgProdOrderMaterialDO> mats = materialMapper.selectByParent(o.getId()).stream()
                .collect(Collectors.toMap(MfgProdOrderMaterialDO::getId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(MfgIssueLineDO::getMaterialId).toList());
        if ("OVER".equals(i.getIssueType())) {
            if (!StringUtils.hasText(i.getOverReason())) throw new BizException(ProductionErrorCodes.ISSUE_OVER_REASON);
            MfgIssueLineDO l = lines.get(0);
            MfgProdOrderMaterialDO m = mats.get(l.getMaterialLineId());
            BigDecimal pct = m == null || m.getRequiredQty().signum() == 0 ? BigDecimal.valueOf(100)
                    : l.getRequestQty().multiply(MfgSupport.HUNDRED).divide(m.getRequiredQty(), 2, RoundingMode.HALF_UP);
            support.fire(MfgStateMachines.MATERIAL_DOC, mapper, i, BIZ_TYPE, MfgAction.SUBMIT, null);
            Map<String, Object> vars = new HashMap<>();
            vars.put("overPct", pct);
            vars.put("amountBase", BigDecimal.ZERO);
            StartResult r = workflowApi.start(ProductionModuleConfig.ISSUE_OVER, id, i.getDocNo(),
                    "超领单 " + i.getDocNo() + " " + MfgSupport.code(ms, l.getMaterialId()) + " × " + MfgSupport.plain(l.getRequestQty()), vars, Map.of(),
                    support.currentUser());
            if (!r.isStarted()) approveAndIssue(i, o);
            return DocResult.of(i.getStatus().name());
        }
        BigDecimal factor = MfgSupport.onePlusPct(support.paramDecimal(ProductionModuleConfig.P_OVER_ISSUE_PCT));
        Map<Long, BigDecimal> pending = pendingRequests(o.getId(), id);
        Map<Long, BigDecimal> requested = new HashMap<>();
        for (MfgIssueLineDO l : lines) requested.merge(l.getMaterialLineId(), l.getRequestQty(), BigDecimal::add);
        for (Map.Entry<Long, BigDecimal> e : requested.entrySet()) {
            MfgProdOrderMaterialDO m = mats.get(e.getKey());
            String code = m == null ? "" : MfgSupport.code(ms, m.getComponentId());
            if (m == null) throw BizException.of(ProductionErrorCodes.ISSUE_LINE_INVALID, 1);
            if ("BACKFLUSH".equals(m.getIssueMethod())) throw BizException.of(ProductionErrorCodes.ISSUE_BACKFLUSH, code);
            BigDecimal open = MfgSupport.max0(MaterialPlanner.openQty(m).subtract(pending.getOrDefault(m.getId(), BigDecimal.ZERO)));
            if (e.getValue().compareTo(open.multiply(factor)) > 0) throw BizException.of(ProductionErrorCodes.ISSUE_OVER, code, MfgSupport.plain(open));
        }
        support.fire(MfgStateMachines.MATERIAL_DOC, mapper, i, BIZ_TYPE, MfgAction.SUBMIT, null);
        approveAndIssue(i, o);
        return DocResult.of(i.getStatus().name());
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!ProductionModuleConfig.ISSUE_OVER.equals(e.getBizType())) return;
        MfgIssueDO i = getOrThrow(e.getBizId());
        if (i.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> approveAndIssue(i, progress.getOrThrow(i.getProdOrderId()));
            case WITHDRAWN -> support.fire(MfgStateMachines.MATERIAL_DOC, mapper, i, BIZ_TYPE, MfgAction.WITHDRAW, null);
            default -> support.fire(MfgStateMachines.MATERIAL_DOC, mapper, i, BIZ_TYPE, MfgAction.REJECT, e.getComment());
        }
    }

    /** 审核通过 → 已提交，生成仓库出库单（仓库自动确认时同一事务内回写，因此生成后重新读取单据） */
    private void approveAndIssue(MfgIssueDO i, MfgProdOrderDO o) {
        i.setSubmittedAt(LocalDateTime.now());
        support.fire(MfgStateMachines.MATERIAL_DOC, mapper, i, BIZ_TYPE, MfgAction.APPROVE, null);
        List<MfgIssueLineDO> lines = lineMapper.selectByParent(i.getId());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(MfgIssueLineDO::getMaterialId).toList());
        List<StockOutRequest.Line> outLines = lines.stream().map(l -> new StockOutRequest.Line(l.getId(), l.getMaterialId(),
                ms.containsKey(l.getMaterialId()) ? ms.get(l.getMaterialId()).baseUom() : null, l.getRequestQty(), null, null)).toList();
        List<Long> outIds = inventoryDocApi.createStockOut(new StockOutRequest(StockOutType.PRODUCTION_ISSUE, new SourceRef(SOURCE_TYPE, i.getId(), i.getDocNo()),
                i.getWarehouseId(), LocalDate.now(), o.getDeptId(), i.getOwnerId(), null, null, outLines));
        MfgIssueDO fresh = getOrThrow(i.getId());
        fresh.setStockOutIds(outIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        mapper.updateByIdOrFail(fresh);
    }

    /** 撤回：待审批撤回审批；已提交且出库单未确认时作废出库单回到草稿 */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        MfgIssueDO i = getOrThrow(id);
        DataScopes.check(i.getOrgId(), i.getDeptId(), i.getOwnerId(), "领料单");
        if (i.getStatus() == DocStatus.PENDING_APPROVAL) {
            workflowApi.withdraw(ProductionModuleConfig.ISSUE_OVER, id, support.currentUser());
            return;
        }
        if (i.getStatus() != DocStatus.APPROVED || "BACKFLUSH".equals(i.getIssueType())) {
            throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, i.getStatus().label(), "撤回");
        }
        try {
            inventoryDocApi.cancelBySource(SOURCE_TYPE, id);
        } catch (BizException e) {
            throw new BizException(ProductionErrorCodes.ISSUE_WITHDRAW_CONFIRMED);
        }
        i.setStockOutIds(null);
        support.fire(MfgStateMachines.MATERIAL_DOC, mapper, i, BIZ_TYPE, MfgAction.WITHDRAW, null);
    }

    // ==================== 倒冲 ====================

    /**
     * 倒冲（R05）：按物料默认仓分组生成 BACKFLUSH 领料单和出库单；可用库存不足时抛出缺料提示。
     * 出库单由仓库确认（参数 inv.out.auto-confirm-source 打开时自动确认），确认后与正常领料一样回写。
     */
    public List<Long> backflush(MfgProdOrderDO o, MfgReportDO report, List<BackflushNeed> needs) {
        List<BackflushNeed> list = needs.stream().filter(n -> n.qty().signum() > 0).toList();
        if (list.isEmpty()) return List.of();
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(n -> n.material().getComponentId()).toList());
        Map<Long, List<BackflushNeed>> byWarehouse = new LinkedHashMap<>();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (BackflushNeed n : list) {
            Long wh = support.warehouseApi().getDefaultWarehouse(n.material().getComponentId(), null).id();
            byWarehouse.computeIfAbsent(wh, k -> new ArrayList<>()).add(n);
            totals.merge(n.material().getComponentId() + "#" + wh, n.qty(), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> e : totals.entrySet()) {
            String[] k = e.getKey().split("#");
            Long componentId = Long.valueOf(k[0]);
            BigDecimal have = inventoryQueryApi.getAvailableQty(componentId, Long.valueOf(k[1]));
            if (have.compareTo(e.getValue()) < 0) {
                throw BizException.of(ProductionErrorCodes.ISSUE_BACKFLUSH_SHORT, MfgSupport.code(ms, componentId), MfgSupport.plain(e.getValue()),
                        MfgSupport.plain(have));
            }
        }
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, List<BackflushNeed>> e : byWarehouse.entrySet()) {
            MfgIssueDO i = newIssue(o, "BACKFLUSH", e.getKey());
            i.setReportId(report.getId());
            i.setRemark("报工单 " + report.getDocNo() + " 倒冲");
            mapper.insert(i);
            int no = 0;
            for (BackflushNeed n : e.getValue()) {
                MfgIssueLineDO line = new MfgIssueLineDO();
                line.setIssueId(i.getId());
                line.setLineNo(++no);
                line.setMaterialLineId(n.material().getId());
                line.setMaterialId(n.material().getComponentId());
                line.setWarehouseId(e.getKey());
                line.setRequestQty(n.qty());
                line.setIssuedQty(BigDecimal.ZERO);
                lineMapper.insert(line);
            }
            support.fire(MfgStateMachines.MATERIAL_DOC, mapper, i, BIZ_TYPE, MfgAction.SUBMIT, null);
            approveAndIssue(i, o);
            ids.add(i.getId());
        }
        return ids;
    }

    /** 报工反审核（R06）：作废未确认的倒冲出库单和倒冲领料单；已确认的需先在仓库反确认 */
    public void reverseBackflush(Long reportId) {
        List<MfgIssueDO> list = mapper.selectList(new LambdaQueryWrapper<MfgIssueDO>().eq(MfgIssueDO::getReportId, reportId)
                .ne(MfgIssueDO::getStatus, DocStatus.VOIDED));
        List<String> confirmed = list.stream().filter(i -> i.getStatus() == DocStatus.COMPLETED)
                .map(i -> i.getStockOutNos() == null ? i.getDocNo() : i.getStockOutNos()).toList();
        if (!confirmed.isEmpty()) throw BizException.of(ProductionErrorCodes.ISSUE_BACKFLUSH_CONFIRMED, String.join("、", confirmed));
        for (MfgIssueDO i : list) {
            if (i.getStatus() == DocStatus.APPROVED) {
                try {
                    inventoryDocApi.cancelBySource(SOURCE_TYPE, i.getId());
                } catch (BizException e) {
                    throw BizException.of(ProductionErrorCodes.ISSUE_BACKFLUSH_CONFIRMED, i.getDocNo());
                }
            }
            support.fire(MfgStateMachines.MATERIAL_DOC, mapper, i, BIZ_TYPE, MfgAction.VOID, "报工反审核");
        }
    }

    // ==================== 仓库回写 ====================

    /** 出库确认（R03）：回写实发、用料已领（超领同时记超领）、追溯；首次领料订单进入生产中 */
    @EventListener
    public void onStockOut(StockOutConfirmedEvent e) {
        if (e.getSource() == null || !SOURCE_TYPE.equals(e.getSource().sourceType())) return;
        MfgIssueDO i = mapper.selectById(e.getSource().sourceId());
        if (i == null || i.getStatus() != DocStatus.APPROVED) return;
        MfgProdOrderDO o = progress.getOrThrow(i.getProdOrderId());
        Map<Long, MfgIssueLineDO> lines = lineMapper.selectByParent(i.getId()).stream().collect(Collectors.toMap(MfgIssueLineDO::getId, Function.identity()));
        Map<Long, MfgProdOrderMaterialDO> mats = new HashMap<>();
        for (StockOutConfirmedEvent.Line l : e.getLines()) {
            MfgIssueLineDO il = lines.get(l.sourceLineId());
            if (il == null) continue;
            il.setIssuedQty(il.getIssuedQty().add(l.baseQty()));
            MfgProdOrderMaterialDO m = mats.computeIfAbsent(il.getMaterialLineId(), materialMapper::selectById);
            if (m != null) {
                m.setIssuedQty(MfgSupport.nz(m.getIssuedQty()).add(l.baseQty()));
                if ("OVER".equals(i.getIssueType())) m.setOverIssuedQty(MfgSupport.nz(m.getOverIssuedQty()).add(l.baseQty()));
            }
            trace.record(o, l.materialId(), l.batchNo(), l.baseQty(), TraceRecorder.STOCK_OUT, e.getStockOutId(), e.getStockOutNo());
        }
        lines.values().forEach(lineMapper::updateByIdOrFail);
        mats.values().stream().filter(Objects::nonNull).forEach(materialMapper::updateByIdOrFail);
        i.setStockOutNos(append(i.getStockOutNos(), e.getStockOutNo()));
        support.fire(MfgStateMachines.MATERIAL_DOC, mapper, i, BIZ_TYPE, MfgAction.COMPLETE, e.getStockOutNo());
        progress.markStarted(o);
    }

    /** 仓库反确认出库单：扣回已领并追加反向追溯；仓库退回（作废）出库单：领料单作废并提醒申请人 */
    @EventListener
    public void onStockDoc(StockDocEvent e) {
        if (!"STOCK_OUT".equals(e.getDocType()) || e.getSource() == null || !SOURCE_TYPE.equals(e.getSource().sourceType())) return;
        MfgIssueDO i = mapper.selectById(e.getSource().sourceId());
        if (i == null) return;
        if (e.getKind() == StockDocEvent.Kind.OUT_REVERSED && i.getStatus() == DocStatus.COMPLETED) {
            for (MfgIssueLineDO il : lineMapper.selectByParent(i.getId())) {
                MfgProdOrderMaterialDO m = materialMapper.selectById(il.getMaterialLineId());
                if (m != null) {
                    m.setIssuedQty(MfgSupport.nz(m.getIssuedQty()).subtract(il.getIssuedQty()));
                    if ("OVER".equals(i.getIssueType())) m.setOverIssuedQty(MfgSupport.max0(MfgSupport.nz(m.getOverIssuedQty()).subtract(il.getIssuedQty())));
                    materialMapper.updateByIdOrFail(m);
                }
                il.setIssuedQty(BigDecimal.ZERO);
                lineMapper.updateByIdOrFail(il);
            }
            trace.reverse(TraceRecorder.STOCK_OUT, e.getDocId());
            support.fire(MfgStateMachines.MATERIAL_DOC, mapper, i, BIZ_TYPE, MfgAction.REOPEN, "出库单 " + e.getDocNo() + " 反确认");
        } else if (e.getKind() == StockDocEvent.Kind.REJECTED && i.getStatus() == DocStatus.APPROVED) {
            support.fire(MfgStateMachines.MATERIAL_DOC, mapper, i, BIZ_TYPE, MfgAction.VOID, "仓库退回：" + (e.getReason() == null ? "" : e.getReason()));
            support.message(List.of(i.getOwnerId()), "领料单被仓库退回", "领料单 " + i.getDocNo() + " 的出库单被仓库退回：" + (e.getReason() == null ? "" : e.getReason()),
                    "/production/issue/" + i.getId());
        }
    }

    static String append(String list, String no) {
        if (no == null) return list;
        return list == null || list.isBlank() ? no : list + "," + no;
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id) {
        IssueDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("issueTypeName", TYPE_NAMES.getOrDefault(d.issueType(), d.issueType()));
        data.put("docDate", d.docDate());
        data.put("prodOrderNo", d.prodOrderNo());
        data.put("productCode", d.productCode());
        data.put("productName", d.productName());
        data.put("warehouseName", d.warehouseName());
        data.put("overReasonName", d.overReason() == null ? null : support.dictLabel("mfg_over_issue_reason", d.overReason()));
        data.put("remark", d.remark());
        data.put("ownerName", d.ownerName());
        data.put("lines", d.lines().stream().map(l -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("lineNo", l.lineNo());
            r.put("materialCode", l.code());
            r.put("materialName", l.name());
            r.put("materialSpec", l.spec());
            r.put("uom", l.uom());
            r.put("requestQty", l.requestQty());
            r.put("issuedQty", l.issuedQty());
            return r;
        }).toList());
        return data;
    }
}
