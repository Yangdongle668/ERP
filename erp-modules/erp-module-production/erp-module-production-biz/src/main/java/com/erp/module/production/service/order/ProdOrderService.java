package com.erp.module.production.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.bom.BomDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPlanAttr;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.routing.RoutingApi;
import com.erp.module.engineering.api.routing.RoutingDTO;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.production.api.ProductionErrorCodes;
import com.erp.module.production.api.order.MrpSuggestion;
import com.erp.module.production.api.order.ProductionOrderClosedEvent;
import com.erp.module.production.api.order.ProductionOrderReleasedEvent;
import com.erp.module.production.api.order.ProductionOrderUnreleasedEvent;
import com.erp.module.production.config.ProductionModuleConfig;
import com.erp.module.production.controller.vo.CommonVOs.BatchResult;
import com.erp.module.production.controller.vo.CommonVOs.DocResult;
import com.erp.module.production.controller.vo.CommonVOs.RelatedDoc;
import com.erp.module.production.controller.vo.CommonVOs.SaveResult;
import com.erp.module.production.controller.vo.ProdOrderVOs.AdjustLine;
import com.erp.module.production.controller.vo.ProdOrderVOs.AdjustReq;
import com.erp.module.production.controller.vo.ProdOrderVOs.KitCheck;
import com.erp.module.production.controller.vo.ProdOrderVOs.MaterialPreview;
import com.erp.module.production.controller.vo.ProdOrderVOs.MaterialResp;
import com.erp.module.production.controller.vo.ProdOrderVOs.MaterialSave;
import com.erp.module.production.controller.vo.ProdOrderVOs.OperationPreview;
import com.erp.module.production.controller.vo.ProdOrderVOs.OperationResp;
import com.erp.module.production.controller.vo.ProdOrderVOs.Option;
import com.erp.module.production.controller.vo.ProdOrderVOs.Preview;
import com.erp.module.production.controller.vo.ProdOrderVOs.ProdOrderDetail;
import com.erp.module.production.controller.vo.ProdOrderVOs.ProdOrderQuery;
import com.erp.module.production.controller.vo.ProdOrderVOs.ProdOrderRow;
import com.erp.module.production.controller.vo.ProdOrderVOs.ProdOrderSave;
import com.erp.module.production.controller.vo.ProdOrderVOs.Shortage;
import com.erp.module.production.controller.vo.ProdOrderVOs.SubstituteOption;
import com.erp.module.production.controller.vo.ProdOrderVOs.Substitution;
import com.erp.module.production.dal.dataobject.MfgDefectDO;
import com.erp.module.production.dal.dataobject.MfgFinishDO;
import com.erp.module.production.dal.dataobject.MfgIssueDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderMaterialDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderOperationDO;
import com.erp.module.production.dal.dataobject.MfgReportDO;
import com.erp.module.production.dal.dataobject.MfgReturnDO;
import com.erp.module.production.dal.dataobject.MfgWorkOrderDO;
import com.erp.module.production.dal.mapper.MfgDefectMapper;
import com.erp.module.production.dal.mapper.MfgFinishMapper;
import com.erp.module.production.dal.mapper.MfgIssueMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMaterialMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderOperationMapper;
import com.erp.module.production.dal.mapper.MfgReportMapper;
import com.erp.module.production.dal.mapper.MfgReturnMapper;
import com.erp.module.production.dal.mapper.MfgWorkOrderMapper;
import com.erp.module.production.service.FinishStatus;
import com.erp.module.production.service.MfgAction;
import com.erp.module.production.service.MfgStateMachines;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.ProdStatus;
import com.erp.module.production.service.WoStatus;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import com.erp.module.quality.api.inspection.InspectionQueryApi;
import com.erp.module.quality.api.inspection.IpqcRejectDTO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 生产订单（需求 09-01）：保存、提交审批、下达（固化用料与工序、齐套检查）、撤销下达、暂停 / 恢复、调整用料、关闭、作废。
 */
@Service("mfgProdOrderService")
public class ProdOrderService {

    public static final String BIZ_TYPE = ProductionModuleConfig.PROD_ORDER;
    static final Set<String> TYPES = Set.of("NORMAL", "SAMPLE", "REWORK");
    static final Map<String, String> TYPE_NAMES = Map.of("NORMAL", "标准", "SAMPLE", "样品", "REWORK", "返工", "DISASSEMBLY", "拆解");
    static final String MRP_SOURCE = "PMC_MRP";
    static final String SINGLE_OPERATION = "完工";

    private final MfgProdOrderMapper mapper;
    private final MfgProdOrderMaterialMapper materialMapper;
    private final MfgProdOrderOperationMapper operationMapper;
    private final MfgWorkOrderMapper workOrderMapper;
    private final MfgIssueMapper issueMapper;
    private final MfgReturnMapper returnMapper;
    private final MfgFinishMapper finishMapper;
    private final MfgReportMapper reportMapper;
    private final MfgDefectMapper defectMapper;
    private final MfgSupport support;
    private final MaterialPlanner planner;
    private final OrderProgressService progress;
    private final RoutingApi routingApi;
    private final WorkflowApi workflowApi;
    private final ObjectProvider<SalesOrderQueryApi> salesOrderQueryApi;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate tx;
    private final ObjectProvider<InspectionQueryApi> inspectionQueryApi;

    public ProdOrderService(MfgProdOrderMapper mapper, MfgProdOrderMaterialMapper materialMapper, MfgProdOrderOperationMapper operationMapper,
                            MfgWorkOrderMapper workOrderMapper, MfgIssueMapper issueMapper, MfgReturnMapper returnMapper, MfgFinishMapper finishMapper,
                            MfgReportMapper reportMapper, MfgDefectMapper defectMapper, MfgSupport support, MaterialPlanner planner,
                            OrderProgressService progress, RoutingApi routingApi, WorkflowApi workflowApi,
                            ObjectProvider<SalesOrderQueryApi> salesOrderQueryApi, DomainEventPublisher eventPublisher,
                            PlatformTransactionManager transactionManager, ObjectProvider<InspectionQueryApi> inspectionQueryApi) {
        this.mapper = mapper;
        this.materialMapper = materialMapper;
        this.operationMapper = operationMapper;
        this.workOrderMapper = workOrderMapper;
        this.issueMapper = issueMapper;
        this.returnMapper = returnMapper;
        this.finishMapper = finishMapper;
        this.reportMapper = reportMapper;
        this.defectMapper = defectMapper;
        this.support = support;
        this.planner = planner;
        this.progress = progress;
        this.routingApi = routingApi;
        this.workflowApi = workflowApi;
        this.salesOrderQueryApi = salesOrderQueryApi;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
        this.inspectionQueryApi = inspectionQueryApi;
    }

    // ==================== 查询 ====================

    public PageResult<ProdOrderRow> page(ProdOrderQuery q) {
        IPage<MfgProdOrderDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), wrapper(q));
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<ProdOrderRow> listForExport(ProdOrderQuery q, int limit) {
        return rows(mapper.selectScopedList(wrapper(q).last("LIMIT " + limit)));
    }

    private LambdaQueryWrapper<MfgProdOrderDO> wrapper(ProdOrderQuery q) {
        LambdaQueryWrapper<MfgProdOrderDO> w = new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getDeleted, false);
        if (StringUtils.hasText(q.getDocNo())) w.likeRight(MfgProdOrderDO::getDocNo, q.getDocNo().trim());
        if (q.getMaterialId() != null) w.eq(MfgProdOrderDO::getMaterialId, q.getMaterialId());
        if (StringUtils.hasText(q.getOrderType())) w.eq(MfgProdOrderDO::getOrderType, q.getOrderType());
        if (StringUtils.hasText(q.getStatuses())) w.in(MfgProdOrderDO::getProdStatus, Arrays.asList(q.getStatuses().split(",")));
        else w.ne(MfgProdOrderDO::getProdStatus, ProdStatus.VOIDED.name());
        if (q.getDeptId() != null) w.in(MfgProdOrderDO::getDeptId, support.deptAndChildren(q.getDeptId()));
        if (q.getPlanFrom() != null) w.ge(MfgProdOrderDO::getPlanStart, q.getPlanFrom());
        if (q.getPlanTo() != null) w.le(MfgProdOrderDO::getPlanStart, q.getPlanTo());
        if (StringUtils.hasText(q.getSalesOrderNo())) w.likeRight(MfgProdOrderDO::getSalesOrderNo, q.getSalesOrderNo().trim());
        if (q.getOwnerId() != null) w.eq(MfgProdOrderDO::getOwnerId, q.getOwnerId());
        if (Boolean.TRUE.equals(q.getOverdue())) {
            w.lt(MfgProdOrderDO::getPlanEnd, LocalDate.now()).in(MfgProdOrderDO::getProdStatus,
                    List.of(ProdStatus.RELEASED.name(), ProdStatus.IN_PROGRESS.name(), ProdStatus.SUSPENDED.name()));
        }
        return w.orderByDesc(MfgProdOrderDO::getId);
    }

    private List<ProdOrderRow> rows(List<MfgProdOrderDO> list) {
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(MfgProdOrderDO::getMaterialId).toList());
        Map<Long, OrgDTO> orgs = support.orgs(list.stream().map(MfgProdOrderDO::getDeptId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(MfgProdOrderDO::getOwnerId).toList());
        LocalDate today = LocalDate.now();
        return list.stream().map(o -> {
            MaterialDTO m = ms.get(o.getMaterialId());
            BigDecimal ratio = o.getQty().signum() == 0 ? BigDecimal.ZERO
                    : o.getQualifiedStockedQty().divide(o.getQty(), 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
            boolean overdue = o.getPlanEnd().isBefore(today) && Set.of(ProdStatus.RELEASED, ProdStatus.IN_PROGRESS, ProdStatus.SUSPENDED)
                    .contains(OrderProgressService.status(o));
            return new ProdOrderRow(o.getId(), o.getDocNo(), o.getOrderType(), o.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.spec(), m == null ? null : m.baseUom(), o.getQty(), o.getCompletedQty(), o.getStockedQty(), o.getQualifiedStockedQty(),
                    o.getScrappedQty(), ratio, o.getPlanStart(), o.getPlanEnd(), overdue, o.getDeptId(), MfgSupport.orgName(orgs, o.getDeptId()),
                    o.getPriority(), o.getSalesOrderNo(), o.getProdStatus(), MfgSupport.name(users, o.getOwnerId()), o.getDocDate());
        }).toList();
    }

    public ProdOrderDetail detail(Long id) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        MaterialDTO product = support.material(o.getMaterialId());
        List<MfgProdOrderMaterialDO> mats = materialMapper.selectByParent(id);
        List<MfgProdOrderOperationDO> ops = operationMapper.selectByParent(id);
        Map<Long, MaterialDTO> ms = support.materials(mats.stream().map(MfgProdOrderMaterialDO::getComponentId).collect(Collectors.toSet()));
        Map<Long, BigDecimal> available = planner.available(ms.keySet());
        Optional<BomDTO> bom = o.getBomId() == null ? Optional.empty() : planner.bomApi().getBom(o.getBomId());
        Map<Long, List<BomDTO.Substitute>> subs = new HashMap<>();
        bom.ifPresent(b -> b.lines().forEach(l -> subs.merge(l.componentId(), l.substitutes() == null ? List.of() : l.substitutes(),
                (a, c) -> { List<BomDTO.Substitute> all = new ArrayList<>(a); all.addAll(c); return all; })));
        Map<Long, MaterialDTO> subMs = support.materials(subs.values().stream().flatMap(List::stream).map(BomDTO.Substitute::substituteId).toList());
        List<MaterialResp> materials = mats.stream().map(m -> {
            MaterialDTO c = ms.get(m.getComponentId());
            List<SubstituteOption> options = subs.getOrDefault(m.getComponentId(), List.of()).stream().map(s -> {
                MaterialDTO sm = subMs.get(s.substituteId());
                return new SubstituteOption(s.substituteId(), sm == null ? null : sm.code(), sm == null ? null : sm.name(), s.ratio());
            }).toList();
            return new MaterialResp(m.getId(), m.getLineNo(), m.getComponentId(), c == null ? null : c.code(), c == null ? null : c.name(),
                    c == null ? null : c.spec(), c == null ? null : c.baseUom(), m.getQtyPer(), m.getScrapRate(), m.getRequiredQty(), m.getIssueMethod(),
                    m.getOperationSeq(), m.getIssuedQty(), m.getOverIssuedQty(), m.getReturnedQty(), m.getReturnedGoodQty(), MaterialPlanner.openQty(m),
                    MaterialPlanner.netQty(m), available.get(m.getComponentId()), m.getSubstituteOfId(), Boolean.TRUE.equals(m.getIsAdded()), options,
                    m.getRemark());
        }).toList();
        Map<Long, WorkCenterDTO> wcs = support.workCenters();
        Map<Integer, IpqcRejectDTO> ipqc = ipqcRejected(id);
        List<OperationResp> operations = ops.stream().map(op -> operationResp(o, ops, op, wcs, ipqc.get(op.getSeq()))).toList();
        Optional<RoutingDTO> routing = o.getRoutingId() == null ? Optional.empty() : routingApi.getRouting(o.getRoutingId());
        BigDecimal pendingDefect = pendingDefectQty(id);
        boolean fqc = support.materialApi().getQualityAttr(o.getMaterialId()).fqcRequired();
        return new ProdOrderDetail(o.getId(), o.getDocNo(), o.getOrderType(), o.getProdStatus(), o.getMaterialId(), product.code(), product.name(),
                product.spec(), product.baseUom(), product.tracking() == null ? null : product.tracking().name(), o.getQty(), o.getBomId(),
                bom.map(BomDTO::docNo).orElse(null), bom.map(BomDTO::version).orElse(null), o.getRoutingId(), routing.map(RoutingDTO::docNo).orElse(null),
                o.getPlanStart(), o.getPlanEnd(), o.getActualStart(), o.getActualEnd(), o.getReleasedAt(), o.getPriority(), o.getBatchNo(),
                o.getSalesOrderLineId(), o.getSalesOrderId(), o.getSalesOrderNo(), o.getSourceType(), o.getSourceId(), o.getSourceNo(), o.getCompletedQty(),
                o.getScrappedQty(), o.getFinishedRequestQty(), o.getStockedQty(), o.getQualifiedStockedQty(), o.getFqcRejectedQty(),
                MfgSupport.max0(o.getCompletedQty().subtract(o.getFinishedRequestQty())), pendingDefect, fqc, o.getDeptId(), support.deptName(o.getDeptId()),
                o.getOwnerId(), support.userName(o.getOwnerId()), o.getCloseReason(), o.getRemark(), o.getCreatedAt(), o.getVersion(), materials, operations,
                related(o));
    }

    /** QC-INS-R09：工序最近一次 IPQC 判定为拒收时显示警示 */
    private Map<Integer, IpqcRejectDTO> ipqcRejected(Long orderId) {
        InspectionQueryApi api = inspectionQueryApi.getIfAvailable();
        if (api == null) return Map.of();
        Map<Integer, IpqcRejectDTO> map = new HashMap<>();
        for (IpqcRejectDTO r : api.getIpqcRejected(orderId)) map.put(r.operationSeq(), r);
        return map;
    }

    private OperationResp operationResp(MfgProdOrderDO o, List<MfgProdOrderOperationDO> ops, MfgProdOrderOperationDO op, Map<Long, WorkCenterDTO> wcs,
                                        IpqcRejectDTO ipqc) {
        WorkCenterDTO wc = op.getWorkCenterId() == null ? null : wcs.get(op.getWorkCenterId());
        BigDecimal std = op.getGoodQty().multiply(op.getStdRunSeconds()).divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);
        BigDecimal reportable = Boolean.TRUE.equals(op.getIsReportPoint())
                ? MfgSupport.max0(progress.inputLimit(o, ops, op.getSeq()).subtract(OrderProgressService.input(op))) : BigDecimal.ZERO;
        return new OperationResp(op.getId(), op.getSeq(), op.getOperation(), op.getWorkCenterId(), wc == null ? null : wc.name(),
                Boolean.TRUE.equals(op.getIsReportPoint()), Boolean.TRUE.equals(op.getIsInspectionPoint()), Boolean.TRUE.equals(op.getIsOutsourced()),
                op.getStdRunSeconds(), op.getStdSetupMinutes(), op.getGoodQty(), op.getDefectQty(), op.getScrapQty(), op.getRepairedQty(),
                op.getDispatchedQty(), op.getActualHours(), std, op.getOpStatus(), reportable, ipqc == null ? null : ipqc.inspectionId(),
                ipqc == null ? null : ipqc.docNo());
    }

    BigDecimal pendingDefectQty(Long orderId) {
        return defectMapper.selectList(new LambdaQueryWrapper<MfgDefectDO>().eq(MfgDefectDO::getProdOrderId, orderId)
                        .eq(MfgDefectDO::getDisposition, "PENDING"))
                .stream().map(d -> d.getQty().subtract(d.getRepairedQty()).subtract(d.getScrappedQty())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<RelatedDoc> related(MfgProdOrderDO o) {
        List<RelatedDoc> list = new ArrayList<>();
        if (o.getSalesOrderId() != null) {
            list.add(new RelatedDoc("UP", "销售订单", o.getSalesOrderNo(), null, null, null, "/sales/order/" + o.getSalesOrderId()));
        }
        if (OrderProgressService.SAMPLE_SOURCE.equals(o.getSourceType())) {
            list.add(new RelatedDoc("UP", "样品单", o.getSourceNo(), null, null, null, "/engineering/sample/" + o.getSourceId()));
        } else if (MRP_SOURCE.equals(o.getSourceType())) {
            list.add(new RelatedDoc("UP", "MRP 生产建议", o.getSourceNo(), null, null, null, null));
        }
        for (MfgIssueDO i : issueMapper.selectList(new LambdaQueryWrapper<MfgIssueDO>().eq(MfgIssueDO::getProdOrderId, o.getId()))) {
            list.add(new RelatedDoc("DOWN", "OVER".equals(i.getIssueType()) ? "超领单" : "BACKFLUSH".equals(i.getIssueType()) ? "倒冲领料" : "领料单",
                    i.getDocNo(), i.getDocDate(), i.getStatus().name(), i.getStatus().label(), "/production/issue/" + i.getId()));
        }
        for (MfgReturnDO r : returnMapper.selectList(new LambdaQueryWrapper<MfgReturnDO>().eq(MfgReturnDO::getProdOrderId, o.getId()))) {
            list.add(new RelatedDoc("DOWN", "退料单", r.getDocNo(), r.getDocDate(), r.getStatus().name(), r.getStatus().label(), "/production/return/" + r.getId()));
        }
        for (MfgFinishDO f : finishMapper.selectList(new LambdaQueryWrapper<MfgFinishDO>().eq(MfgFinishDO::getProdOrderId, o.getId()))) {
            FinishStatus fs = FinishStatus.valueOf(f.getFinishStatus());
            list.add(new RelatedDoc("DOWN", "完工入库申请", f.getDocNo(), f.getDocDate(), fs.name(), fs.label(), "/production/finish?prodOrderId=" + o.getId()));
        }
        return list;
    }

    // ==================== 编辑页预览 ====================

    /** 草稿用料预览（根据 BOM 实时展开）、工序预览、默认车间与提前期 */
    public Preview preview(Long materialId, Long bomId, Long routingId, BigDecimal qty, String orderType) {
        MaterialDTO product = support.material(materialId);
        BigDecimal q = qty == null || qty.signum() <= 0 ? BigDecimal.ONE : qty;
        Optional<BomDTO> def = planner.bomApi().getDefaultBom(materialId, LocalDate.now());
        Optional<BomDTO> bom = bomId != null ? planner.bomApi().getBom(bomId) : def;
        List<Option> boms = new ArrayList<>();
        def.ifPresent(b -> boms.add(new Option(b.id(), b.docNo() + " V" + b.version(), true)));
        bom.filter(b -> def.isEmpty() || !def.get().id().equals(b.id())).ifPresent(b -> boms.add(new Option(b.id(), b.docNo() + " V" + b.version(), false)));
        Optional<RoutingDTO> defRouting = routingApi.getDefaultRouting(materialId);
        Optional<RoutingDTO> routing = routingId != null ? routingApi.getRouting(routingId) : defRouting;
        List<Option> routings = new ArrayList<>();
        defRouting.ifPresent(r -> routings.add(new Option(r.id(), r.docNo() + " V" + r.version(), true)));
        routing.filter(r -> defRouting.isEmpty() || !defRouting.get().id().equals(r.id()))
                .ifPresent(r -> routings.add(new Option(r.id(), r.docNo() + " V" + r.version(), false)));
        List<MaterialPreview> materials = new ArrayList<>();
        if (!"REWORK".equals(orderType) && bom.isPresent()) {
            List<MaterialPlanner.PlannedLine> lines = planner.explode(bom.get());
            Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(MaterialPlanner.PlannedLine::componentId).toList());
            Map<Long, BigDecimal> available = planner.available(ms.keySet());
            int no = 0;
            for (MaterialPlanner.PlannedLine l : lines) {
                MaterialDTO c = ms.get(l.componentId());
                BigDecimal required = planner.required(q, l.qtyPer(), l.scrapRate(), c == null ? null : c.baseUom());
                BigDecimal avail = available.getOrDefault(l.componentId(), BigDecimal.ZERO);
                materials.add(new MaterialPreview(++no, l.componentId(), c == null ? null : c.code(), c == null ? null : c.name(), c == null ? null : c.spec(),
                        c == null ? null : c.baseUom(), l.qtyPer(), l.scrapRate(), required, l.issueMethod(), l.operationSeq(), avail,
                        avail.compareTo(required) < 0));
            }
        }
        Map<Long, WorkCenterDTO> wcs = support.workCenters();
        List<OperationPreview> ops = routing.map(r -> r.steps().stream().map(s -> {
            WorkCenterDTO wc = s.workCenterId() == null ? null : wcs.get(s.workCenterId());
            return new OperationPreview(s.seq(), s.operation(), s.workCenterId(), wc == null ? null : wc.name(), s.runSeconds(), s.setupMinutes(),
                    s.reportPoint(), s.inspectionPoint(), s.outsourced());
        }).toList()).orElse(List.of());
        Long dept = defaultDept(routing.orElse(null), wcs);
        MaterialPlanAttr plan = support.materialApi().getPlanAttr(product.id());
        return new Preview(bom.map(BomDTO::id).orElse(null), bom.map(BomDTO::docNo).orElse(null), bom.map(BomDTO::version).orElse(null), boms,
                routing.map(RoutingDTO::id).orElse(null), routing.map(RoutingDTO::docNo).orElse(null), routings, dept,
                plan == null ? 0 : plan.leadTimeDays(), materials, ops);
    }

    private static Long defaultDept(RoutingDTO routing, Map<Long, WorkCenterDTO> wcs) {
        if (routing == null) return null;
        for (RoutingDTO.Step s : routing.steps()) {
            WorkCenterDTO wc = s.workCenterId() == null ? null : wcs.get(s.workCenterId());
            if (wc != null && wc.deptId() != null) return wc.deptId();
        }
        return null;
    }

    // ==================== 保存 ====================

    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(ProdOrderSave req) {
        MfgProdOrderDO o = new MfgProdOrderDO();
        o.setDocNo(support.nextNo(BIZ_TYPE));
        o.setDocDate(LocalDate.now());
        o.setProdStatus(ProdStatus.DRAFT.name());
        o.setStatus(ProdStatus.DRAFT.docStatus());
        List<String> warnings = fill(o, req, null);
        mapper.insert(o);
        saveReworkMaterials(o, req);
        return new SaveResult(o.getId(), warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult update(Long id, ProdOrderSave req) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        ProdStatus s = OrderProgressService.status(o);
        if (s == ProdStatus.PENDING) throw new BizException(ProductionErrorCodes.DOC_PENDING);
        if (s != ProdStatus.DRAFT && s != ProdStatus.PLANNED) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, s.label(), "修改");
        if (req.version() != null) o.setVersion(req.version());
        List<String> warnings = fill(o, req, s);
        mapper.updateByIdOrFail(o);
        saveReworkMaterials(o, req);
        return new SaveResult(o.getId(), warnings);
    }

    /** 单头字段与校验（R01、R02）；已计划时只能改数量、BOM、工艺、日期、车间、优先级、备注 */
    private List<String> fill(MfgProdOrderDO o, ProdOrderSave req, ProdStatus current) {
        List<String> warnings = new ArrayList<>();
        boolean draft = current == null || current == ProdStatus.DRAFT;
        String type = StringUtils.hasText(req.orderType()) ? req.orderType() : "NORMAL";
        if ("DISASSEMBLY".equals(type)) throw BizException.of(ProductionErrorCodes.ORDER_TYPE_UNSUPPORTED, "拆解");
        if (!TYPES.contains(type)) throw BizException.of(ProductionErrorCodes.ORDER_TYPE_UNSUPPORTED, type);
        if (req.qty() == null || req.qty().signum() <= 0) throw BizException.of(ProductionErrorCodes.LINE_QTY_POSITIVE, 1);
        if (req.planEnd().isBefore(req.planStart())) throw new BizException(ProductionErrorCodes.ORDER_DATE_RANGE);
        int priority = req.priority() == null ? 5 : req.priority();
        if (priority < 1 || priority > 9) throw new BizException(ProductionErrorCodes.ORDER_PRIORITY);
        if (draft) {
            MaterialDTO m = support.materialApi().validateUsable(req.materialId());
            if (m.materialType() != MaterialType.FINISHED && m.materialType() != MaterialType.SEMI_FINISHED) {
                throw BizException.of(ProductionErrorCodes.ORDER_MATERIAL_NOT_MAKE, m.code());
            }
            o.setOrderType(type);
            o.setMaterialId(m.id());
            if (req.salesOrderLineId() != null) {
                SalesOrderLineDTO line = salesLine(req.salesOrderLineId());
                if (!Objects.equals(line.materialId(), m.id())) throw new BizException(ProductionErrorCodes.ORDER_SALES_LINE_MISMATCH);
                o.setSalesOrderLineId(line.lineId());
                o.setSalesOrderId(line.orderId());
                o.setSalesOrderNo(line.orderNo());
            } else {
                o.setSalesOrderLineId(null);
                o.setSalesOrderId(null);
                o.setSalesOrderNo(null);
            }
            o.setBatchNo(StringUtils.hasText(req.batchNo()) ? req.batchNo().trim() : o.getDocNo());
        }
        MaterialDTO product = support.material(o.getMaterialId());
        if (!"REWORK".equals(o.getOrderType())) {
            Optional<BomDTO> bom = req.bomId() != null ? planner.bomApi().getBom(req.bomId()) : planner.bomApi().getDefaultBom(product.id(), LocalDate.now());
            if (bom.isEmpty() || bom.get().status() != DocStatus.APPROVED || !Objects.equals(bom.get().materialId(), product.id())) {
                throw BizException.of(ProductionErrorCodes.ORDER_NO_BOM, product.code());
            }
            o.setBomId(bom.get().id());
        } else {
            o.setBomId(null);
        }
        Optional<RoutingDTO> routing = req.routingId() != null ? routingApi.getRouting(req.routingId()) : routingApi.getDefaultRouting(product.id());
        o.setRoutingId(routing.map(RoutingDTO::id).orElse(null));
        if (routing.isEmpty()) warnings.add("产品没有工艺路线，按单工序“完工”报工");
        o.setQty(support.round(req.qty(), product.baseUom()));
        o.setPlanStart(req.planStart());
        o.setPlanEnd(req.planEnd());
        o.setPriority(priority);
        o.setRemark(MfgSupport.trim(req.remark()));
        Long dept = req.deptId() != null ? req.deptId() : defaultDept(routing.orElse(null), support.workCenters());
        if (current == null) support.fillOwner(o, null, dept);
        else if (dept != null) o.setDeptId(dept);
        if (o.getCompletedQty() == null) initQuantities(o);
        return warnings;
    }

    private static void initQuantities(MfgProdOrderDO o) {
        o.setCompletedQty(BigDecimal.ZERO);
        o.setScrappedQty(BigDecimal.ZERO);
        o.setFinishedRequestQty(BigDecimal.ZERO);
        o.setStockedQty(BigDecimal.ZERO);
        o.setQualifiedStockedQty(BigDecimal.ZERO);
        o.setFqcRejectedQty(BigDecimal.ZERO);
    }

    private SalesOrderLineDTO salesLine(Long lineId) {
        SalesOrderQueryApi api = salesOrderQueryApi.getIfAvailable();
        if (api == null) throw new BizException(ProductionErrorCodes.ORDER_SALES_LINE_MISMATCH);
        return api.getLine(lineId).orElseThrow(() -> new BizException(ProductionErrorCodes.ORDER_SALES_LINE_MISMATCH));
    }

    /** 返工订单的投入物料：默认“产品本身 × 1”，可增加补充料 */
    private void saveReworkMaterials(MfgProdOrderDO o, ProdOrderSave req) {
        if (!"REWORK".equals(o.getOrderType())) {
            materialMapper.deleteByParent(o.getId());
            return;
        }
        List<MaterialSave> list = req.materials() == null || req.materials().isEmpty()
                ? List.of(new MaterialSave(o.getMaterialId(), BigDecimal.ONE, BigDecimal.ZERO, "PICK", null, "返工产品"))
                : req.materials();
        materialMapper.deleteByParent(o.getId());
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(MaterialSave::componentId).toList());
        int no = 0;
        for (MaterialSave s : list) {
            MaterialDTO c = ms.get(s.componentId());
            if (c == null || c.status() != MaterialStatus.ENABLED) support.materialApi().validateUsable(s.componentId());
            if (s.qtyPer() == null || s.qtyPer().signum() <= 0) throw BizException.of(ProductionErrorCodes.LINE_QTY_POSITIVE, no + 1);
            MfgProdOrderMaterialDO m = newMaterial(o.getId(), ++no, s.componentId(), s.qtyPer(), MfgSupport.nz(s.scrapRate()),
                    "BACKFLUSH".equals(s.issueMethod()) ? "BACKFLUSH" : "PICK", s.operationSeq());
            m.setRequiredQty(planner.required(o.getQty(), m.getQtyPer(), m.getScrapRate(), c == null ? null : c.baseUom()));
            m.setIsAdded(!Objects.equals(s.componentId(), o.getMaterialId()));
            m.setRemark(MfgSupport.trim(s.remark()));
            materialMapper.insert(m);
        }
    }

    static MfgProdOrderMaterialDO newMaterial(Long orderId, int lineNo, Long componentId, BigDecimal qtyPer, BigDecimal scrapRate, String method, Integer seq) {
        MfgProdOrderMaterialDO m = new MfgProdOrderMaterialDO();
        m.setProdOrderId(orderId);
        m.setLineNo(lineNo);
        m.setComponentId(componentId);
        m.setQtyPer(qtyPer);
        m.setScrapRate(scrapRate);
        m.setIssueMethod(method);
        m.setOperationSeq(seq);
        m.setIssuedQty(BigDecimal.ZERO);
        m.setOverIssuedQty(BigDecimal.ZERO);
        m.setReturnedQty(BigDecimal.ZERO);
        m.setReturnedGoodQty(BigDecimal.ZERO);
        m.setIsAdded(false);
        return m;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        ProdStatus s = OrderProgressService.status(o);
        if (s == ProdStatus.PENDING) throw new BizException(ProductionErrorCodes.DOC_PENDING);
        if (s != ProdStatus.DRAFT && s != ProdStatus.PLANNED) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, s.label(), "删除");
        materialMapper.deleteByParent(id);
        mapper.deleteById(id);
        support.log(BIZ_TYPE, id, o.getDocNo(), "DELETE", "删除", s.name(), null, null);
    }

    // ==================== 提交 / 审批 ====================

    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        if (OrderProgressService.status(o) != ProdStatus.DRAFT) throw new BizException(ProductionErrorCodes.DOC_NOT_EDITABLE);
        MaterialDTO m = support.materialApi().validateUsable(o.getMaterialId());
        if (!"REWORK".equals(o.getOrderType()) && (o.getBomId() == null || planner.bomApi().getBom(o.getBomId()).isEmpty())) {
            throw BizException.of(ProductionErrorCodes.ORDER_NO_BOM, m.code());
        }
        if ("REWORK".equals(o.getOrderType()) && materialMapper.selectByParent(id).isEmpty()) throw new BizException(ProductionErrorCodes.ORDER_REWORK_MATERIALS);
        progress.fire(o, MfgAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("orderType", o.getOrderType());
        vars.put("qty", o.getQty());
        StartResult r = workflowApi.start(BIZ_TYPE, id, o.getDocNo(), "生产订单 " + o.getDocNo() + " " + m.code() + " × " + MfgSupport.plain(o.getQty()),
                vars, Map.of(), support.currentUser());
        if (!r.isStarted()) progress.fire(o, MfgAction.APPROVE, null);
        return DocResult.of(o.getProdStatus());
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        MfgProdOrderDO o = progress.getOrThrow(e.getBizId());
        if (OrderProgressService.status(o) != ProdStatus.PENDING) return;
        switch (e.getResult()) {
            case APPROVED -> progress.fire(o, MfgAction.APPROVE, null);
            case WITHDRAWN -> progress.fire(o, MfgAction.WITHDRAW, null);
            default -> progress.fire(o, MfgAction.REJECT, e.getComment());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        if (OrderProgressService.status(o) != ProdStatus.PENDING) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, OrderProgressService.status(o).label(), "撤回");
        workflowApi.withdraw(BIZ_TYPE, id, support.currentUser());
    }

    // ==================== 计划日期（PMC 排产回写） ====================

    @Transactional(rollbackFor = Exception.class)
    public void updatePlanDates(Long id, LocalDate planStart, LocalDate planEnd, String reason) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        ProdStatus s = OrderProgressService.status(o);
        if (s != ProdStatus.PLANNED && !ProdStatus.RUNNING.contains(s) && s != ProdStatus.SUSPENDED) {
            throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, s.label(), "修改计划日期");
        }
        if (planStart == null || planEnd == null) return;
        if (planEnd.isBefore(planStart)) throw new BizException(ProductionErrorCodes.ORDER_DATE_RANGE);
        if (planStart.equals(o.getPlanStart()) && planEnd.equals(o.getPlanEnd())) return;
        String text = "计划日期 " + o.getPlanStart() + "~" + o.getPlanEnd() + " → " + planStart + "~" + planEnd
                + (StringUtils.hasText(reason) ? "（" + reason.trim() + "）" : "");
        o.setPlanStart(planStart);
        o.setPlanEnd(planEnd);
        mapper.updateByIdOrFail(o);
        support.log(ProductionModuleConfig.PROD_ORDER, o.getId(), o.getDocNo(), "RESCHEDULE", "调整计划日期", s.name(), s.name(),
                text.length() > 256 ? text.substring(0, 256) : text);
    }

    // ==================== 下达 ====================

    /** 下达（R03、R04）：齐套检查 → 固化用料与工序 → 已下达 → 发布下达事件 */
    @Transactional(rollbackFor = Exception.class)
    public DocResult release(Long id, boolean confirmShortage) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        ProdStatus s = OrderProgressService.status(o);
        if (s != ProdStatus.PLANNED) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, s.label(), "下达");
        MaterialDTO product = support.materialApi().validateUsable(o.getMaterialId());
        List<MfgProdOrderMaterialDO> mats = buildMaterials(o, product);
        List<String> warnings = new ArrayList<>();
        String level = support.params().getString(ProductionModuleConfig.P_KIT_CHECK);
        if (!"NONE".equals(level)) {
            Map<Long, BigDecimal> needs = new LinkedHashMap<>();
            for (MfgProdOrderMaterialDO m : mats) needs.merge(m.getComponentId(), m.getRequiredQty(), BigDecimal::add);
            List<Shortage> shortages = planner.shortages(needs, o.getId(), null);
            if (!shortages.isEmpty()) {
                String text = MaterialPlanner.shortageText(shortages);
                if ("BLOCK".equals(level)) throw BizException.of(ProductionErrorCodes.ORDER_NOT_KIT, text).withData(Map.of("shortages", shortages));
                if (!confirmShortage) {
                    throw BizException.of(ProductionErrorCodes.ORDER_NOT_KIT, text)
                            .withData(Map.of("needConfirm", true, "message", "以下物料不齐套：" + text, "shortages", shortages));
                }
                warnings.add("以下物料不齐套：" + text);
            }
        }
        materialMapper.deleteByParent(o.getId());
        for (MfgProdOrderMaterialDO m : mats) {
            m.setId(null);
            materialMapper.insert(m);
        }
        operationMapper.deleteByParent(o.getId());
        for (MfgProdOrderOperationDO op : buildOperations(o)) operationMapper.insert(op);
        o.setReleasedAt(LocalDateTime.now());
        progress.fire(o, MfgAction.RELEASE, warnings.isEmpty() ? null : String.join("；", warnings));
        eventPublisher.publish(new ProductionOrderReleasedEvent(o.getId(), o.getDocNo(), o.getMaterialId(), o.getQty(), o.getPlanEnd()));
        return new DocResult(o.getProdStatus(), warnings);
    }

    /** 批量下达：逐张独立事务；缺料需确认时计入失败原因 */
    public BatchResult batchRelease(List<Long> ids, boolean confirmShortage) {
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            try {
                tx.executeWithoutResult(st -> release(id, confirmShortage));
                ok++;
            } catch (BizException e) {
                MfgProdOrderDO o = mapper.selectById(id);
                errors.add((o == null ? String.valueOf(id) : o.getDocNo()) + "：" + e.getMessage());
            }
        }
        return new BatchResult(ok, errors);
    }

    /** 用料快照：标准/样品按 BOM 展开；返工按已录入的投入物料重算应领 */
    private List<MfgProdOrderMaterialDO> buildMaterials(MfgProdOrderDO o, MaterialDTO product) {
        List<MfgProdOrderMaterialDO> out = new ArrayList<>();
        if ("REWORK".equals(o.getOrderType())) {
            List<MfgProdOrderMaterialDO> saved = materialMapper.selectByParent(o.getId());
            if (saved.isEmpty()) throw new BizException(ProductionErrorCodes.ORDER_REWORK_MATERIALS);
            Map<Long, MaterialDTO> ms = support.materials(saved.stream().map(MfgProdOrderMaterialDO::getComponentId).toList());
            for (MfgProdOrderMaterialDO m : saved) {
                MaterialDTO c = ms.get(m.getComponentId());
                m.setRequiredQty(planner.required(o.getQty(), m.getQtyPer(), m.getScrapRate(), c == null ? null : c.baseUom()));
                out.add(m);
            }
            return out;
        }
        BomDTO bom = (o.getBomId() == null ? Optional.<BomDTO>empty() : planner.bomApi().getBom(o.getBomId()))
                .orElseThrow(() -> BizException.of(ProductionErrorCodes.ORDER_NO_BOM, product.code()));
        List<MaterialPlanner.PlannedLine> lines = planner.explode(bom);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(MaterialPlanner.PlannedLine::componentId).toList());
        int no = 0;
        for (MaterialPlanner.PlannedLine l : lines) {
            MfgProdOrderMaterialDO m = newMaterial(o.getId(), ++no, l.componentId(), l.qtyPer(), l.scrapRate(), l.issueMethod(), l.operationSeq());
            MaterialDTO c = ms.get(l.componentId());
            m.setRequiredQty(planner.required(o.getQty(), l.qtyPer(), l.scrapRate(), c == null ? null : c.baseUom()));
            out.add(m);
        }
        return out;
    }

    /** 工序快照：按工艺路线；没有工艺路线时为单工序“完工”；末道工序强制为报工点 */
    private List<MfgProdOrderOperationDO> buildOperations(MfgProdOrderDO o) {
        List<MfgProdOrderOperationDO> out = new ArrayList<>();
        Optional<RoutingDTO> routing = o.getRoutingId() == null ? Optional.empty() : routingApi.getRouting(o.getRoutingId());
        List<RoutingDTO.Step> steps = routing.map(RoutingDTO::steps).orElse(List.of());
        if (steps.isEmpty()) {
            out.add(newOperation(o.getId(), 10, SINGLE_OPERATION, null, true, false, false, BigDecimal.ZERO, BigDecimal.ZERO));
            return out;
        }
        List<RoutingDTO.Step> sorted = steps.stream().sorted((a, b) -> Integer.compare(a.seq(), b.seq())).toList();
        for (int i = 0; i < sorted.size(); i++) {
            RoutingDTO.Step s = sorted.get(i);
            boolean last = i == sorted.size() - 1;
            out.add(newOperation(o.getId(), s.seq(), s.operation(), s.workCenterId(), s.reportPoint() || last, s.inspectionPoint(), s.outsourced(),
                    MfgSupport.nz(s.runSeconds()), MfgSupport.nz(s.setupMinutes())));
        }
        return out;
    }

    private static MfgProdOrderOperationDO newOperation(Long orderId, int seq, String operation, Long wc, boolean reportPoint, boolean inspection,
                                                        boolean outsourced, BigDecimal run, BigDecimal setup) {
        MfgProdOrderOperationDO op = new MfgProdOrderOperationDO();
        op.setProdOrderId(orderId);
        op.setSeq(seq);
        op.setOperation(operation);
        op.setWorkCenterId(wc);
        op.setIsReportPoint(reportPoint);
        op.setIsInspectionPoint(inspection);
        op.setIsOutsourced(outsourced);
        op.setStdRunSeconds(run);
        op.setStdSetupMinutes(setup);
        op.setGoodQty(BigDecimal.ZERO);
        op.setFirstGoodQty(BigDecimal.ZERO);
        op.setDefectQty(BigDecimal.ZERO);
        op.setScrapQty(BigDecimal.ZERO);
        op.setRepairedQty(BigDecimal.ZERO);
        op.setDispatchedQty(BigDecimal.ZERO);
        op.setActualHours(BigDecimal.ZERO);
        op.setOpStatus("WAITING");
        return op;
    }

    /** 齐套检查：已计划按 BOM 预展开；已下达按未领数量（只扣除先于本单下达的订单） */
    public KitCheck kitCheck(Long id) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        Map<Long, BigDecimal> needs = new LinkedHashMap<>();
        ProdStatus s = OrderProgressService.status(o);
        if (ProdStatus.ACTIVE.contains(s)) {
            for (MfgProdOrderMaterialDO m : materialMapper.selectByParent(id)) needs.merge(m.getComponentId(), MaterialPlanner.openQty(m), BigDecimal::add);
        } else {
            for (MfgProdOrderMaterialDO m : buildMaterials(o, support.material(o.getMaterialId()))) {
                needs.merge(m.getComponentId(), m.getRequiredQty(), BigDecimal::add);
            }
        }
        List<Shortage> list = planner.shortages(needs, o.getId(), ProdStatus.ACTIVE.contains(s) ? o.getReleasedAt() : null);
        return new KitCheck(o.getId(), o.getDocNo(), list.isEmpty(), list);
    }

    /** 撤销下达：未领料、未报工 → 已计划，删除用料与工序快照、未报工的工单 */
    @Transactional(rollbackFor = Exception.class)
    public void unrelease(Long id) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        ProdStatus s = OrderProgressService.status(o);
        if (s != ProdStatus.RELEASED) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, s.label(), "撤销下达");
        boolean issued = materialMapper.selectByParent(id).stream().anyMatch(m -> MfgSupport.nz(m.getIssuedQty()).signum() > 0)
                || issueMapper.selectCount(new LambdaQueryWrapper<MfgIssueDO>().eq(MfgIssueDO::getProdOrderId, id)
                .in(MfgIssueDO::getStatus, List.of(DocStatus.PENDING_APPROVAL, DocStatus.APPROVED, DocStatus.COMPLETED))) > 0;
        boolean reported = reportMapper.selectCount(new LambdaQueryWrapper<MfgReportDO>().eq(MfgReportDO::getProdOrderId, id)) > 0;
        if (issued || reported) throw new BizException(ProductionErrorCodes.ORDER_UNRELEASE_BLOCKED);
        issueMapper.delete(new LambdaQueryWrapper<MfgIssueDO>().eq(MfgIssueDO::getProdOrderId, id).eq(MfgIssueDO::getStatus, DocStatus.DRAFT));
        workOrderMapper.delete(new LambdaQueryWrapper<MfgWorkOrderDO>().eq(MfgWorkOrderDO::getProdOrderId, id));
        if (!"REWORK".equals(o.getOrderType())) materialMapper.deleteByParent(id);
        operationMapper.deleteByParent(id);
        o.setReleasedAt(null);
        progress.fire(o, MfgAction.UNRELEASE, null);
        eventPublisher.publish(new ProductionOrderUnreleasedEvent(o.getId(), o.getDocNo(), o.getMaterialId()));
    }

    // ==================== 暂停 / 恢复 ====================

    @Transactional(rollbackFor = Exception.class)
    public void suspend(Long id, String reason) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        String why = MfgSupport.requireReason(reason, "暂停");
        ProdStatus s = OrderProgressService.status(o);
        if (!ProdStatus.RUNNING.contains(s)) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, s.label(), "暂停");
        o.setStatusBeforeSuspend(s.name());
        progress.fire(o, MfgAction.SUSPEND, why);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        ProdStatus s = OrderProgressService.status(o);
        if (s != ProdStatus.SUSPENDED) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, s.label(), "恢复");
        MfgAction action = ProdStatus.IN_PROGRESS.name().equals(o.getStatusBeforeSuspend()) ? MfgAction.RESUME_RUN : MfgAction.RESUME;
        o.setStatusBeforeSuspend(null);
        progress.fire(o, action, null);
    }

    // ==================== 调整用料 ====================

    /** 调整用料（R05、R06）：修改应领、新增、删除、替代、修改计划数量；原因必填并记入操作日志 */
    @Transactional(rollbackFor = Exception.class)
    public void adjustMaterials(Long id, AdjustReq req) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        ProdStatus s = OrderProgressService.status(o);
        if (!ProdStatus.RUNNING.contains(s)) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, s.label(), "调整用料");
        if (!StringUtils.hasText(req.reason())) throw new BizException(ProductionErrorCodes.ORDER_ADJUST_REASON);
        if (req.version() != null) o.setVersion(req.version());
        List<MfgProdOrderMaterialDO> mats = materialMapper.selectByParent(id);
        Map<Long, MfgProdOrderMaterialDO> byId = mats.stream().collect(Collectors.toMap(MfgProdOrderMaterialDO::getId, m -> m));
        Set<Long> componentIds = new HashSet<>(mats.stream().map(MfgProdOrderMaterialDO::getComponentId).toList());
        if (req.lines() != null) req.lines().stream().map(AdjustLine::componentId).filter(Objects::nonNull).forEach(componentIds::add);
        if (req.substitutions() != null) req.substitutions().stream().map(Substitution::substituteId).forEach(componentIds::add);
        Map<Long, MaterialDTO> ms = support.materials(componentIds);
        List<String> changes = new ArrayList<>();
        Set<Long> touched = new HashSet<>();

        // R06：修改计划数量，BOM 行应领按比例重算（不低于已领 − 已退）
        if (req.qty() != null && req.qty().compareTo(o.getQty()) != 0) {
            if (req.qty().compareTo(o.getCompletedQty()) < 0) throw BizException.of(ProductionErrorCodes.ORDER_QTY_BELOW_COMPLETED, MfgSupport.plain(o.getCompletedQty()));
            if (req.qty().signum() <= 0) throw BizException.of(ProductionErrorCodes.LINE_QTY_POSITIVE, 1);
            changes.add("计划数量 " + MfgSupport.plain(o.getQty()) + " → " + MfgSupport.plain(req.qty()));
            o.setQty(req.qty());
            for (MfgProdOrderMaterialDO m : mats) {
                if (Boolean.TRUE.equals(m.getIsAdded()) || m.getSubstituteOfId() != null) continue;
                MaterialDTO c = ms.get(m.getComponentId());
                BigDecimal required = planner.required(o.getQty(), m.getQtyPer(), m.getScrapRate(), c == null ? null : c.baseUom());
                m.setRequiredQty(required.max(MaterialPlanner.netQty(m)));
                touched.add(m.getId());
            }
        }
        int nextNo = mats.stream().mapToInt(MfgProdOrderMaterialDO::getLineNo).max().orElse(0);
        List<MfgProdOrderMaterialDO> added = new ArrayList<>();
        Set<Long> deleted = new HashSet<>();
        for (AdjustLine l : req.lines() == null ? List.<AdjustLine>of() : req.lines()) {
            if (l.id() == null) {
                if (l.componentId() == null) continue;
                MaterialDTO c = support.materialApi().validateUsable(l.componentId());
                if (l.requiredQty() == null || l.requiredQty().signum() <= 0) throw BizException.of(ProductionErrorCodes.LINE_QTY_POSITIVE, nextNo + 1);
                BigDecimal qtyPer = l.qtyPer() != null ? l.qtyPer() : l.requiredQty().divide(o.getQty(), 6, RoundingMode.HALF_UP);
                MfgProdOrderMaterialDO m = newMaterial(id, ++nextNo, c.id(), qtyPer, BigDecimal.ZERO, "BACKFLUSH".equals(l.issueMethod()) ? "BACKFLUSH" : "PICK",
                        l.operationSeq());
                m.setRequiredQty(support.round(l.requiredQty(), c.baseUom()));
                m.setIsAdded(true);
                m.setRemark(MfgSupport.trim(l.remark()));
                added.add(m);
                changes.add("新增 " + c.code() + " 应领 " + MfgSupport.plain(m.getRequiredQty()));
                continue;
            }
            MfgProdOrderMaterialDO m = byId.get(l.id());
            if (m == null) throw BizException.of(ProductionErrorCodes.ISSUE_LINE_INVALID, l.id());
            String code = MfgSupport.code(ms, m.getComponentId());
            if (Boolean.TRUE.equals(l.delete())) {
                if (MfgSupport.nz(m.getIssuedQty()).signum() > 0) throw BizException.of(ProductionErrorCodes.ORDER_MATERIAL_DELETE_ISSUED, code);
                deleted.add(m.getId());
                changes.add("删除 " + code);
                continue;
            }
            if (l.requiredQty() != null && l.requiredQty().compareTo(m.getRequiredQty()) != 0) {
                BigDecimal net = MaterialPlanner.netQty(m);
                if (l.requiredQty().compareTo(net) < 0) throw BizException.of(ProductionErrorCodes.ORDER_REQUIRED_BELOW_ISSUED, code, MfgSupport.plain(net));
                changes.add(code + " 应领 " + MfgSupport.plain(m.getRequiredQty()) + " → " + MfgSupport.plain(l.requiredQty()));
                m.setRequiredQty(l.requiredQty());
                touched.add(m.getId());
            }
            if (StringUtils.hasText(l.issueMethod()) && !l.issueMethod().equals(m.getIssueMethod())) {
                m.setIssueMethod("BACKFLUSH".equals(l.issueMethod()) ? "BACKFLUSH" : "PICK");
                touched.add(m.getId());
            }
            if (l.remark() != null) {
                m.setRemark(MfgSupport.trim(l.remark()));
                touched.add(m.getId());
            }
        }
        // 替代：按 BOM 定义的替代料与比例生成替代行，减少原行应领
        BomDTO bom = o.getBomId() == null ? null : planner.bomApi().getBom(o.getBomId()).orElse(null);
        for (Substitution sub : req.substitutions() == null ? List.<Substitution>of() : req.substitutions()) {
            MfgProdOrderMaterialDO m = byId.get(sub.lineId());
            if (m == null) throw BizException.of(ProductionErrorCodes.ISSUE_LINE_INVALID, sub.lineId());
            String code = MfgSupport.code(ms, m.getComponentId());
            BomDTO.Substitute def = bom == null ? null : bom.lines().stream().filter(l -> l.componentId().equals(m.getComponentId()))
                    .flatMap(l -> l.substitutes() == null ? java.util.stream.Stream.<BomDTO.Substitute>empty() : l.substitutes().stream())
                    .filter(x -> x.substituteId().equals(sub.substituteId())).findFirst().orElse(null);
            if (def == null) throw BizException.of(ProductionErrorCodes.ORDER_SUBSTITUTE_INVALID, MfgSupport.code(ms, sub.substituteId()));
            if (sub.qty() == null || sub.qty().signum() <= 0) throw BizException.of(ProductionErrorCodes.LINE_QTY_POSITIVE, m.getLineNo());
            BigDecimal remain = m.getRequiredQty().subtract(sub.qty());
            BigDecimal net = MaterialPlanner.netQty(m);
            if (remain.compareTo(net) < 0) throw BizException.of(ProductionErrorCodes.ORDER_REQUIRED_BELOW_ISSUED, code, MfgSupport.plain(net));
            m.setRequiredQty(remain);
            touched.add(m.getId());
            MaterialDTO sc = ms.get(sub.substituteId());
            BigDecimal ratio = MfgSupport.nz(def.ratio()).signum() > 0 ? def.ratio() : BigDecimal.ONE;
            MfgProdOrderMaterialDO n = newMaterial(id, ++nextNo, sub.substituteId(), m.getQtyPer().multiply(ratio).setScale(6, RoundingMode.HALF_UP),
                    m.getScrapRate(), m.getIssueMethod(), m.getOperationSeq());
            n.setRequiredQty(support.roundUp(sub.qty().multiply(ratio), sc == null ? null : sc.baseUom()));
            n.setSubstituteOfId(m.getId());
            n.setRemark("替代 " + code);
            added.add(n);
            changes.add(code + " 替代为 " + MfgSupport.code(ms, sub.substituteId()) + " " + MfgSupport.plain(n.getRequiredQty()));
        }
        for (MfgProdOrderMaterialDO m : mats) {
            if (deleted.contains(m.getId())) materialMapper.deleteById(m.getId());
            else if (touched.contains(m.getId())) materialMapper.updateByIdOrFail(m);
        }
        for (MfgProdOrderMaterialDO m : added) materialMapper.insert(m);
        mapper.updateByIdOrFail(o);
        support.log(BIZ_TYPE, o.getId(), o.getDocNo(), "ADJUST_MATERIAL", "调整用料", o.getProdStatus(), o.getProdStatus(),
                req.reason().trim() + (changes.isEmpty() ? "" : "：" + String.join("；", changes)));
    }

    // ==================== 关闭 / 作废 ====================

    /**
     * 关闭（R08）：原因必填（正常完工除外）；存在未完成的领料/退料/入库单据时不能关闭；
     * 余料按参数检查；未报工的在制数量、待处理不良按报废处理需确认。
     */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String reason, boolean confirm) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        ProdStatus s = OrderProgressService.status(o);
        if (!ProdStatus.ACTIVE.contains(s)) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, s.label(), "关闭");
        String why = s == ProdStatus.COMPLETED && !StringUtils.hasText(reason) ? "正常完工" : MfgSupport.requireReason(reason, "关闭");
        List<String> open = new ArrayList<>();
        issueMapper.selectList(new LambdaQueryWrapper<MfgIssueDO>().eq(MfgIssueDO::getProdOrderId, id)
                .in(MfgIssueDO::getStatus, List.of(DocStatus.PENDING_APPROVAL, DocStatus.APPROVED))).forEach(i -> open.add(i.getDocNo()));
        returnMapper.selectList(new LambdaQueryWrapper<MfgReturnDO>().eq(MfgReturnDO::getProdOrderId, id)
                .in(MfgReturnDO::getStatus, List.of(DocStatus.PENDING_APPROVAL, DocStatus.APPROVED))).forEach(r -> open.add(r.getDocNo()));
        finishMapper.selectList(new LambdaQueryWrapper<MfgFinishDO>().eq(MfgFinishDO::getProdOrderId, id)
                .eq(MfgFinishDO::getFinishStatus, FinishStatus.SUBMITTED.name())).forEach(f -> open.add(f.getDocNo()));
        if (!open.isEmpty()) throw BizException.of(ProductionErrorCodes.ORDER_OPEN_DOCS, String.join("、", open));
        List<String> confirms = new ArrayList<>();
        String level = support.params().getString(ProductionModuleConfig.P_CLOSE_REQUIRE_RETURN);
        if (!"NONE".equals(level)) {
            List<MfgProdOrderMaterialDO> mats = materialMapper.selectByParent(id);
            Map<Long, MaterialDTO> ms = support.materials(mats.stream().map(MfgProdOrderMaterialDO::getComponentId).toList());
            List<String> remains = new ArrayList<>();
            for (MfgProdOrderMaterialDO m : mats) {
                MaterialDTO c = ms.get(m.getComponentId());
                BigDecimal remain = MaterialPlanner.netQty(m).subtract(planner.theoretical(o, m, c == null ? null : c.baseUom()));
                if (remain.signum() > 0) remains.add((c == null ? String.valueOf(m.getComponentId()) : c.name()) + " " + MfgSupport.plain(remain));
            }
            if (!remains.isEmpty()) {
                String text = String.join("、", remains);
                if ("BLOCK".equals(level)) throw BizException.of(ProductionErrorCodes.ORDER_REMAINING_MATERIAL, text);
                confirms.add("还有余料未退回：" + text);
            }
        }
        BigDecimal wip = o.getQty().subtract(o.getCompletedQty()).subtract(o.getScrappedQty());
        if (wip.signum() > 0 && s != ProdStatus.COMPLETED) confirms.add("还有未完工的在制数量 " + MfgSupport.plain(wip) + "，关闭后按报废处理");
        BigDecimal pending = pendingDefectQty(id);
        if (pending.signum() > 0) confirms.add("还有 " + MfgSupport.plain(pending) + " 待处理不良");
        if (!confirms.isEmpty() && !confirm) {
            String text = String.join("；", confirms);
            throw BizException.of(ProductionErrorCodes.ORDER_CLOSE_CONFIRM, text).withData(Map.of("needConfirm", true, "message", text, "items", confirms));
        }
        // 未报工的工单自动取消（有报工的完成），草稿领料/退料作废
        for (MfgWorkOrderDO w : workOrderMapper.selectList(new LambdaQueryWrapper<MfgWorkOrderDO>().eq(MfgWorkOrderDO::getProdOrderId, id)
                .in(MfgWorkOrderDO::getWoStatus, List.of(WoStatus.DISPATCHED.name(), WoStatus.RUNNING.name())))) {
            WoStatus to = MfgStateMachines.WORK_ORDER.fire(WoStatus.valueOf(w.getWoStatus()),
                    WoStatus.DISPATCHED.name().equals(w.getWoStatus()) ? MfgAction.CANCEL : MfgAction.COMPLETE);
            w.setWoStatus(to.name());
            w.setStatus(to.docStatus());
            workOrderMapper.updateByIdOrFail(w);
        }
        for (MfgIssueDO i : issueMapper.selectList(new LambdaQueryWrapper<MfgIssueDO>().eq(MfgIssueDO::getProdOrderId, id).eq(MfgIssueDO::getStatus, DocStatus.DRAFT))) {
            support.fire(MfgStateMachines.MATERIAL_DOC, issueMapper, i, ProductionModuleConfig.ISSUE, MfgAction.VOID, "生产订单关闭");
        }
        for (MfgReturnDO r : returnMapper.selectList(new LambdaQueryWrapper<MfgReturnDO>().eq(MfgReturnDO::getProdOrderId, id).eq(MfgReturnDO::getStatus, DocStatus.DRAFT))) {
            support.fire(MfgStateMachines.MATERIAL_DOC, returnMapper, r, ProductionModuleConfig.RETURN, MfgAction.VOID, "生产订单关闭");
        }
        o.setCloseReason(why);
        if (o.getActualEnd() == null) o.setActualEnd(LocalDateTime.now());
        progress.fire(o, MfgAction.CLOSE, why);
        eventPublisher.publish(new ProductionOrderClosedEvent(o.getId(), o.getDocNo(), o.getMaterialId(), why));
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidOrder(Long id, String reason) {
        MfgProdOrderDO o = progress.getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        String why = MfgSupport.requireReason(reason, "作废");
        ProdStatus s = OrderProgressService.status(o);
        if (s == ProdStatus.PENDING) throw new BizException(ProductionErrorCodes.DOC_PENDING);
        if (s != ProdStatus.DRAFT && s != ProdStatus.PLANNED) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, s.label(), "作废");
        o.setCloseReason(why);
        progress.fire(o, MfgAction.VOID, why);
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id) {
        ProdOrderDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("orderTypeName", TYPE_NAMES.getOrDefault(d.orderType(), d.orderType()));
        data.put("materialCode", d.materialCode());
        data.put("materialName", d.materialName());
        data.put("materialSpec", d.materialSpec());
        data.put("qty", d.qty());
        data.put("uom", d.baseUom());
        data.put("batchNo", d.batchNo());
        data.put("planStart", d.planStart());
        data.put("planEnd", d.planEnd());
        data.put("deptName", d.deptName());
        data.put("bomNo", d.bomNo() == null ? null : d.bomNo() + " V" + d.bomVersion());
        data.put("salesOrderNo", d.salesOrderNo());
        data.put("remark", d.remark());
        data.put("materials", d.materials().stream().map(m -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("lineNo", m.lineNo());
            r.put("code", m.code());
            r.put("name", m.name());
            r.put("qtyPer", m.qtyPer());
            r.put("requiredQty", m.requiredQty());
            r.put("issueMethod", "BACKFLUSH".equals(m.issueMethod()) ? "倒冲" : "领料");
            return r;
        }).toList());
        data.put("operations", d.operations().stream().map(op -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("seq", op.seq());
            r.put("operation", op.operation());
            r.put("workCenterName", op.workCenterName());
            r.put("barcode", d.docNo() + "#" + op.seq());
            return r;
        }).toList());
        return data;
    }

    // ==================== 其他模块生成 ====================

    /** MRP 转单（R09）：直接为“已计划”，记录来源建议 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createFromMrp(List<MrpSuggestion> suggestions) {
        List<Long> ids = new ArrayList<>();
        for (MrpSuggestion s : suggestions) {
            LocalDate start = s.planStart() != null ? s.planStart() : LocalDate.now();
            LocalDate end = s.planEnd() != null && !s.planEnd().isBefore(start) ? s.planEnd() : start;
            ids.add(createPlanned(new ProdOrderSave("NORMAL", s.materialId(), s.qty(), null, null, start, end, s.deptId(), 5, s.salesOrderLineId(),
                    null, s.remark(), null, null), MRP_SOURCE, s.suggestionId(), s.suggestionNo(), s.plannerId()));
        }
        return ids;
    }

    /** 样品生产订单（R10）：类型 SAMPLE，已计划 */
    @Transactional(rollbackFor = Exception.class)
    public MfgProdOrderDO createSample(Long sampleId, String sampleNo, Long materialId, BigDecimal qty, LocalDate requiredDate) {
        LocalDate start = LocalDate.now();
        LocalDate end = requiredDate != null && !requiredDate.isBefore(start) ? requiredDate : start;
        Long id = createPlanned(new ProdOrderSave("SAMPLE", materialId, qty, null, null, start, end, null, 3, null, null, "样品单 " + sampleNo, null, null),
                OrderProgressService.SAMPLE_SOURCE, sampleId, sampleNo, null);
        return mapper.selectById(id);
    }

    private Long createPlanned(ProdOrderSave req, String sourceType, Long sourceId, String sourceNo, Long plannerId) {
        MfgProdOrderDO o = new MfgProdOrderDO();
        o.setDocNo(support.nextNo(BIZ_TYPE));
        o.setDocDate(LocalDate.now());
        o.setProdStatus(ProdStatus.PLANNED.name());
        o.setStatus(ProdStatus.PLANNED.docStatus());
        o.setSourceType(sourceType);
        o.setSourceId(sourceId);
        o.setSourceNo(sourceNo);
        fill(o, req, null);
        if (plannerId != null) support.fillOwner(o, plannerId, o.getDeptId());
        mapper.insert(o);
        support.log(BIZ_TYPE, o.getId(), o.getDocNo(), "CREATE", "生成", null, ProdStatus.PLANNED.name(), sourceNo);
        return o.getId();
    }
}
