package com.erp.module.purchase.service.requisition;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPurchaseAttr;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.SourceType;
import com.erp.module.purchase.api.PurchaseErrorCodes;
import com.erp.module.purchase.api.requisition.MrpPurchaseSuggestion;
import com.erp.module.purchase.api.requisition.PurchaseRequisitionClosedEvent;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.CommonVOs.RelatedDoc;
import com.erp.module.purchase.controller.vo.PriceVOs.EffectivePrice;
import com.erp.module.purchase.controller.vo.RequisitionVOs.PendingLine;
import com.erp.module.purchase.controller.vo.RequisitionVOs.PendingQuery;
import com.erp.module.purchase.controller.vo.RequisitionVOs.ReqDetail;
import com.erp.module.purchase.controller.vo.RequisitionVOs.ReqLineResp;
import com.erp.module.purchase.controller.vo.RequisitionVOs.ReqLineSave;
import com.erp.module.purchase.controller.vo.RequisitionVOs.ReqQuery;
import com.erp.module.purchase.controller.vo.RequisitionVOs.ReqRow;
import com.erp.module.purchase.controller.vo.RequisitionVOs.ReqSave;
import com.erp.module.purchase.dal.dataobject.IdQtyRow;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import com.erp.module.purchase.dal.dataobject.RequisitionDO;
import com.erp.module.purchase.dal.dataobject.RequisitionLineDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.mapper.OrderLineMapper;
import com.erp.module.purchase.dal.mapper.OrderMapper;
import com.erp.module.purchase.dal.mapper.RequisitionLineMapper;
import com.erp.module.purchase.dal.mapper.RequisitionMapper;
import com.erp.module.purchase.service.PurAction;
import com.erp.module.purchase.service.PurStateMachines;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.purchase.service.price.PriceService;
import com.erp.module.purchase.service.supplier.SupplierService;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购申请（需求 07-03）：草稿 → 待审批 → 已审核 → 执行中（部分转订单）→ 已完成（全部转订单）/ 已关闭。
 * 已转订单数量由采购订单审核、反审核、变更时重新汇总（R03、R04：最多记到申请数量）。
 */
@Service
public class RequisitionService {

    public static final String BIZ_TYPE = PurchaseModuleConfig.REQUISITION;
    public static final String OPEN = "OPEN";
    public static final String ORDERED = "ORDERED";
    public static final String CLOSED = "CLOSED";
    static final int MAX_PENDING = 2000;

    private final RequisitionMapper mapper;
    private final RequisitionLineMapper lineMapper;
    private final OrderLineMapper orderLineMapper;
    private final OrderMapper orderMapper;
    private final SupplierService supplierService;
    private final PriceService priceService;
    private final PurSupport support;
    private final CurrencyApi currencyApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;

    public RequisitionService(RequisitionMapper mapper, RequisitionLineMapper lineMapper, OrderLineMapper orderLineMapper, OrderMapper orderMapper,
                              SupplierService supplierService, PriceService priceService, PurSupport support, CurrencyApi currencyApi,
                              WorkflowApi workflowApi, FileApi fileApi, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.orderLineMapper = orderLineMapper;
        this.orderMapper = orderMapper;
        this.supplierService = supplierService;
        this.priceService = priceService;
        this.support = support;
        this.currencyApi = currencyApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 查询 ====================

    public PageResult<ReqRow> page(ReqQuery q) {
        LambdaQueryWrapper<RequisitionDO> w = new LambdaQueryWrapper<RequisitionDO>().eq(RequisitionDO::getDeleted, false)
                .likeRight(StringUtils.hasText(q.getDocNo()), RequisitionDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(StringUtils.hasText(q.getRequisitionType()), RequisitionDO::getRequisitionType, q.getRequisitionType())
                .eq(q.getRequestDeptId() != null, RequisitionDO::getRequestDeptId, q.getRequestDeptId())
                .eq(q.getOwnerId() != null, RequisitionDO::getOwnerId, q.getOwnerId())
                .eq(q.getUrgent() != null, RequisitionDO::getUrgent, q.getUrgent());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(RequisitionDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        StringBuilder lineCond = new StringBuilder();
        if (q.getMaterialId() != null) lineCond.append(" AND material_id = ").append(q.getMaterialId().longValue());
        if (q.getRequiredFrom() != null) lineCond.append(" AND required_date >= '").append(q.getRequiredFrom()).append("'");
        if (q.getRequiredTo() != null) lineCond.append(" AND required_date <= '").append(q.getRequiredTo()).append("'");
        if (!lineCond.isEmpty()) w.inSql(RequisitionDO::getId, "SELECT requisition_id FROM pur_requisition_line WHERE deleted = 0" + lineCond);
        w.orderByDesc(RequisitionDO::getDocDate).orderByDesc(RequisitionDO::getId);
        IPage<RequisitionDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        List<RequisitionDO> list = page.getRecords();
        Map<Long, List<RequisitionLineDO>> lines = lineMapper.selectByParents(list.stream().map(RequisitionDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(RequisitionLineDO::getRequisitionId));
        Map<Long, MaterialDTO> ms = support.materials(lines.values().stream().flatMap(List::stream).map(RequisitionLineDO::getMaterialId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(RequisitionDO::getOwnerId).toList());
        Map<Long, OrgDTO> depts = support.orgs(list.stream().map(RequisitionDO::getRequestDeptId).toList());
        return new PageResult<>(list.stream().map(r -> {
            List<RequisitionLineDO> ls = lines.getOrDefault(r.getId(), List.of());
            return new ReqRow(r.getId(), r.getDocNo(), r.getDocDate(), r.getRequisitionType(), r.getRequestDeptId(),
                    depts.containsKey(r.getRequestDeptId()) ? depts.get(r.getRequestDeptId()).name() : null, r.getOwnerId(), PurSupport.name(users, r.getOwnerId()),
                    summary(ms, ls.stream().map(RequisitionLineDO::getMaterialId).toList()), ls.size(),
                    (int) ls.stream().filter(l -> ORDERED.equals(l.getLineStatus())).count(), Boolean.TRUE.equals(r.getUrgent()),
                    ls.stream().map(RequisitionLineDO::getRequiredDate).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null), r.getStatus().name());
        }).toList(), page.getTotal());
    }

    /** 物料摘要：第一个物料名称 + “等 N 项” */
    public static String summary(Map<Long, MaterialDTO> ms, List<Long> ids) {
        if (ids.isEmpty()) return "";
        MaterialDTO first = ms.get(ids.get(0));
        String name = first == null ? "" : first.code() + " " + first.name();
        return ids.size() > 1 ? name + " 等 " + ids.size() + " 项" : name;
    }

    public ReqDetail detail(Long id) {
        RequisitionDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "采购申请");
        List<RequisitionLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(RequisitionLineDO::getMaterialId).toList());
        Map<Long, SupplierDO> ss = supplierService.byIds(lines.stream().map(RequisitionLineDO::getSuggestedSupplierId).toList());
        boolean price = PurSupport.canViewPrice();
        String deptName = r.getRequestDeptId() == null ? null : support.orgs(List.of(r.getRequestDeptId())).values().stream().findFirst().map(OrgDTO::name).orElse(null);
        List<ReqLineResp> resp = lines.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            SupplierDO s = ss.get(l.getSuggestedSupplierId());
            return new ReqLineResp(l.getId(), l.getLineNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.spec(), m == null ? null : m.baseUom(), l.getUom(), l.getQty(), l.getBaseQty(), l.getRequiredDate(),
                    l.getSuggestedSupplierId(), s == null ? null : s.getShortName(), PurSupport.mask(l.getReferencePrice(), price), l.getPurpose(),
                    l.getOrderedQty(), l.getLineStatus(), l.getMrpResultId(), l.getSourceDemand(), l.getRemark());
        }).toList();
        return new ReqDetail(r.getId(), r.getDocNo(), r.getDocDate(), r.getStatus().name(), r.getRequisitionType(), r.getRequestDeptId(), deptName,
                Boolean.TRUE.equals(r.getUrgent()), r.getMrpRunId(), r.getOwnerId(), support.userName(r.getOwnerId()), r.getRemark(), r.getCreatedAt(),
                r.getVersion(), price, resp, related(lines));
    }

    private List<RelatedDoc> related(List<RequisitionLineDO> lines) {
        if (lines.isEmpty()) return List.of();
        List<OrderLineDO> ols = orderLineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>()
                .in(OrderLineDO::getRequisitionLineId, lines.stream().map(RequisitionLineDO::getId).toList()));
        if (ols.isEmpty()) return List.of();
        return orderMapper.selectBatchIds(ols.stream().map(OrderLineDO::getOrderId).collect(Collectors.toSet())).stream()
                .sorted(Comparator.comparing(OrderDO::getId))
                .map(o -> new RelatedDoc("DOWN", "采购订单", o.getDocNo(), o.getDocDate(), o.getStatus().name(), o.getStatus().label(), "/purchase/order/" + o.getId()))
                .toList();
    }

    /** 待转订单明细：已审核/执行中申请中未完全转单的行（按采购员数据权限：非“全部”范围时只看物料采购员或申请人为自己的行） */
    public PageResult<PendingLine> pending(PendingQuery q) {
        List<RequisitionDO> heads = mapper.selectList(new LambdaQueryWrapper<RequisitionDO>()
                .in(RequisitionDO::getStatus, DocStatus.APPROVED, DocStatus.IN_PROGRESS)
                .likeRight(StringUtils.hasText(q.getDocNo()), RequisitionDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase()));
        if (heads.isEmpty()) return PageResult.empty();
        Map<Long, RequisitionDO> byId = heads.stream().collect(Collectors.toMap(RequisitionDO::getId, r -> r));
        LambdaQueryWrapper<RequisitionLineDO> w = new LambdaQueryWrapper<RequisitionLineDO>().in(RequisitionLineDO::getRequisitionId, byId.keySet())
                .eq(RequisitionLineDO::getLineStatus, OPEN)
                .eq(q.getMaterialId() != null, RequisitionLineDO::getMaterialId, q.getMaterialId())
                .eq(q.getSupplierId() != null, RequisitionLineDO::getSuggestedSupplierId, q.getSupplierId())
                .ge(q.getRequiredFrom() != null, RequisitionLineDO::getRequiredDate, q.getRequiredFrom())
                .le(q.getRequiredTo() != null, RequisitionLineDO::getRequiredDate, q.getRequiredTo())
                .orderByAsc(RequisitionLineDO::getRequiredDate).orderByAsc(RequisitionLineDO::getId).last("LIMIT " + MAX_PENDING);
        if (StringUtils.hasText(q.getLineIds())) {
            w.in(RequisitionLineDO::getId, Arrays.stream(q.getLineIds().split(",")).map(String::trim).filter(x -> x.matches("\\d+")).map(Long::valueOf).toList());
        }
        List<RequisitionLineDO> lines = lineMapper.selectList(w).stream().filter(l -> l.getOrderedQty().compareTo(l.getBaseQty()) < 0).toList();
        Map<Long, MaterialPurchaseAttr> attrs = new HashMap<>();
        boolean all = SecurityUtils.currentDataScope().all();
        Long me = support.currentUser();
        if (!all) {
            lines = lines.stream().filter(l -> {
                MaterialPurchaseAttr a = attrs.computeIfAbsent(l.getMaterialId(), k -> support.materialApi().getPurchaseAttr(k));
                return Objects.equals(a.buyerId(), me) || Objects.equals(byId.get(l.getRequisitionId()).getOwnerId(), me)
                        || DataScopes.visible(byId.get(l.getRequisitionId()).getOrgId(), byId.get(l.getRequisitionId()).getDeptId(), null);
            }).toList();
        }
        int from = Math.min((q.getPageNo() - 1) * q.getPageSize(), lines.size());
        List<RequisitionLineDO> page = lines.subList(from, Math.min(from + q.getPageSize(), lines.size()));
        Map<Long, MaterialDTO> ms = support.materials(page.stream().map(RequisitionLineDO::getMaterialId).toList());
        Map<Long, SupplierDO> ss = supplierService.byIds(page.stream().map(RequisitionLineDO::getSuggestedSupplierId).toList());
        Map<Long, UserDTO> users = support.users(page.stream().map(l -> byId.get(l.getRequisitionId()).getOwnerId()).toList());
        boolean price = PurSupport.canViewPrice();
        return new PageResult<>(page.stream().map(l -> {
            RequisitionDO r = byId.get(l.getRequisitionId());
            MaterialDTO m = ms.get(l.getMaterialId());
            SupplierDO s = ss.get(l.getSuggestedSupplierId());
            MaterialPurchaseAttr a = attrs.computeIfAbsent(l.getMaterialId(), k -> support.materialApi().getPurchaseAttr(k));
            return new PendingLine(l.getId(), r.getId(), r.getDocNo(), l.getLineNo(), r.getRequisitionType(), Boolean.TRUE.equals(r.getUrgent()),
                    l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(),
                    l.getBaseQty(), l.getOrderedQty(), l.getBaseQty().subtract(l.getOrderedQty()), l.getRequiredDate(), a.leadTimeDays(),
                    l.getSuggestedSupplierId(), s == null ? null : s.getShortName(), PurSupport.mask(l.getReferencePrice(), price),
                    PurSupport.name(users, r.getOwnerId()), l.getSourceDemand());
        }).toList(), lines.size());
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(ReqSave req) {
        RequisitionDO r = new RequisitionDO();
        r.setDocNo(support.nextNo(BIZ_TYPE));
        r.setDocDate(LocalDate.now());
        r.setStatus(DocStatus.DRAFT);
        support.fillOwner(r, null);
        fillHeader(r, req);
        mapper.insert(r);
        saveLines(r, req.lines());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, r.getId());
        return r.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ReqSave req) {
        RequisitionDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "采购申请");
        PurSupport.requireDraft(r);
        if (req.version() != null) r.setVersion(req.version());
        fillHeader(r, req);
        mapper.updateByIdOrFail(r);
        saveLines(r, req.lines());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, r.getId());
    }

    private void fillHeader(RequisitionDO r, ReqSave req) {
        String type = StringUtils.hasText(req.requisitionType()) ? req.requisitionType() : "MANUAL";
        if (!type.equals(r.getRequisitionType())) support.dict().validate("pur_requisition_type", type, "申请类型");
        r.setRequisitionType(type);
        r.setRequestDeptId(req.requestDeptId() != null ? req.requestDeptId() : (r.getRequestDeptId() != null ? r.getRequestDeptId() : r.getDeptId()));
        r.setUrgent(Boolean.TRUE.equals(req.urgent()));
        r.setRemark(PurSupport.trim(req.remark()));
    }

    private void saveLines(RequisitionDO r, List<ReqLineSave> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(ReqLineSave::materialId).toList());
        lineMapper.deleteByParent(r.getId());
        int no = 0;
        for (ReqLineSave l : lines) {
            no++;
            MaterialDTO m = ms.get(l.materialId());
            if (m == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "物料");
            if (l.qty() == null || l.qty().signum() <= 0) throw BizException.of(PurchaseErrorCodes.LINE_QTY_POSITIVE, no);
            if (l.requiredDate() == null) throw BizException.of(PurchaseErrorCodes.LINE_FIELD_REQUIRED, no, "需求日期");
            RequisitionLineDO d = new RequisitionLineDO();
            d.setRequisitionId(r.getId());
            d.setLineNo(no);
            fillLine(d, m, l.uom(), l.qty(), l.requiredDate(), l.suggestedSupplierId());
            d.setPurpose(PurSupport.trim(l.purpose()));
            d.setRemark(PurSupport.trim(l.remark()));
            lineMapper.insert(d);
        }
    }

    /** 单位默认物料采购单位；建议供应商默认物料默认供应商；参考单价取建议供应商的有效价（每业务单位，不含税） */
    private void fillLine(RequisitionLineDO d, MaterialDTO m, String uom, BigDecimal qty, LocalDate requiredDate, Long supplierId) {
        String u = StringUtils.hasText(uom) ? uom : support.materialApi().getPurchaseAttr(m.id()).purchaseUom();
        d.setMaterialId(m.id());
        d.setUom(u);
        d.setQty(Decimals.qty(qty));
        d.setBaseQty(Decimals.qty(support.toBase(m.id(), qty, u)));
        d.setRequiredDate(requiredDate);
        Long sid = supplierId != null ? supplierId : supplierService.defaultSupplier(m.id()).map(SupplierDO::getId).orElse(null);
        d.setSuggestedSupplierId(sid);
        d.setReferencePrice(null);
        if (sid != null) {
            SupplierDO s = supplierService.getOrThrow(sid);
            EffectivePrice p = priceService.effectiveForUom(sid, m.id(), qty, u, LocalDate.now(), s.getCurrency());
            d.setReferencePrice(p == null ? null : p.price());
        }
        d.setOrderedQty(d.getOrderedQty() == null ? BigDecimal.ZERO : d.getOrderedQty());
        d.setLineStatus(OPEN);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        RequisitionDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "采购申请");
        PurSupport.requireDraft(r);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        RequisitionDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "采购申请");
        support.fire(PurStateMachines.EXECUTABLE, mapper, r, BIZ_TYPE, PurAction.VOID, PurSupport.trim(reason));
    }

    // ==================== 提交 / 审核 ====================

    /** R01：物料启用且取得方式为采购或委外；需求日期不早于今天 */
    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        RequisitionDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "采购申请");
        return submit(r);
    }

    private DocResult submit(RequisitionDO r) {
        PurSupport.requireDraft(r);
        List<RequisitionLineDO> lines = lineMapper.selectByParent(r.getId());
        if (lines.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        LocalDate today = LocalDate.now();
        BigDecimal amountBase = BigDecimal.ZERO;
        Map<Long, SupplierDO> ss = supplierService.byIds(lines.stream().map(RequisitionLineDO::getSuggestedSupplierId).toList());
        for (RequisitionLineDO l : lines) {
            MaterialDTO m = support.materialApi().validateUsable(l.getMaterialId());
            if (m.sourceType() == SourceType.MAKE) throw BizException.of(PurchaseErrorCodes.MATERIAL_NOT_PURCHASABLE, m.code());
            if (l.getRequiredDate().isBefore(today)) throw BizException.of(PurchaseErrorCodes.REQUIRED_DATE_PAST, l.getLineNo());
            SupplierDO s = ss.get(l.getSuggestedSupplierId());
            if (l.getReferencePrice() != null && s != null) {
                amountBase = amountBase.add(currencyApi.toBase(Decimals.multiplyAmount(l.getQty(), l.getReferencePrice()), currencyApi.getRate(s.getCurrency(), today)));
            }
        }
        support.fire(PurStateMachines.EXECUTABLE, mapper, r, BIZ_TYPE, PurAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("requisitionType", r.getRequisitionType());
        vars.put("amountBase", amountBase);
        StartResult res = workflowApi.start(BIZ_TYPE, r.getId(), r.getDocNo(), "采购申请 " + r.getDocNo(), vars, Map.of(), support.currentUser());
        if (!res.isStarted()) support.fire(PurStateMachines.EXECUTABLE, mapper, r, BIZ_TYPE, PurAction.APPROVE, null);
        return DocResult.of(r.getStatus().name());
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        RequisitionDO r = getOrThrow(e.getBizId());
        if (r.getStatus() != DocStatus.PENDING_APPROVAL) return;
        PurAction a = switch (e.getResult()) {
            case APPROVED -> PurAction.APPROVE;
            case WITHDRAWN -> PurAction.WITHDRAW;
            default -> PurAction.REJECT;
        };
        support.fire(PurStateMachines.EXECUTABLE, mapper, r, BIZ_TYPE, a, a == PurAction.REJECT ? e.getComment() : null);
    }

    /** R05：已有行转订单（含草稿订单引用）时不能反审核 */
    @Transactional(rollbackFor = Exception.class)
    public void unapprove(Long id, String reason) {
        RequisitionDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "采购申请");
        String why = PurSupport.requireReason(reason, "反审核");
        List<RequisitionLineDO> lines = lineMapper.selectByParent(id);
        if (lines.stream().anyMatch(l -> l.getOrderedQty().signum() > 0)
                || orderLineMapper.countByRequisitionLines(lines.stream().map(RequisitionLineDO::getId).toList()) > 0) {
            throw new BizException(PurchaseErrorCodes.REQ_HAS_ORDER);
        }
        support.fire(PurStateMachines.EXECUTABLE, mapper, r, BIZ_TYPE, PurAction.UNAPPROVE, why);
    }

    /** 关闭：剩余未转行关闭，不再出现在待转明细；发布事件让 PMC 重新计算（R06） */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String reason) {
        RequisitionDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "采购申请");
        String why = PurSupport.requireReason(reason, "关闭");
        List<RequisitionLineDO> lines = lineMapper.selectByParent(id);
        List<Long> closed = new ArrayList<>();
        for (RequisitionLineDO l : lines) {
            if (OPEN.equals(l.getLineStatus())) {
                l.setLineStatus(CLOSED);
                lineMapper.updateByIdOrFail(l);
                closed.add(l.getId());
            }
        }
        support.fire(PurStateMachines.EXECUTABLE, mapper, r, BIZ_TYPE, PurAction.CLOSE, why);
        List<RequisitionLineDO> cl = lines.stream().filter(l -> closed.contains(l.getId())).toList();
        eventPublisher.publish(new PurchaseRequisitionClosedEvent(r.getId(), r.getDocNo(),
                cl.stream().map(RequisitionLineDO::getMrpResultId).filter(Objects::nonNull).toList(),
                cl.stream().map(RequisitionLineDO::getMaterialId).distinct().toList()));
    }

    // ==================== 回写（R03、R04） ====================

    /** 重新汇总申请行已转订单数量（最多记到申请数量）并刷新行状态与单据状态 */
    @Transactional(rollbackFor = Exception.class)
    public void refreshOrdered(Collection<Long> requisitionLineIds) {
        Set<Long> ids = requisitionLineIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return;
        Map<Long, BigDecimal> sums = orderLineMapper.sumOrderedByRequisitionLines(ids).stream()
                .collect(Collectors.toMap(IdQtyRow::getId, IdQtyRow::getQty));
        Set<Long> heads = new HashSet<>();
        for (RequisitionLineDO l : lineMapper.selectBatchIds(ids)) {
            BigDecimal ordered = sums.getOrDefault(l.getId(), BigDecimal.ZERO).min(l.getBaseQty());
            String status = CLOSED.equals(l.getLineStatus()) ? CLOSED : ordered.compareTo(l.getBaseQty()) >= 0 ? ORDERED : OPEN;
            if (ordered.compareTo(l.getOrderedQty()) != 0 || !status.equals(l.getLineStatus())) {
                l.setOrderedQty(ordered);
                l.setLineStatus(status);
                lineMapper.updateByIdOrFail(l);
            }
            heads.add(l.getRequisitionId());
        }
        for (Long hid : heads) refreshStatus(getOrThrow(hid));
    }

    private void refreshStatus(RequisitionDO r) {
        if (r.getStatus() != DocStatus.APPROVED && r.getStatus() != DocStatus.IN_PROGRESS && r.getStatus() != DocStatus.COMPLETED) return;
        List<RequisitionLineDO> lines = lineMapper.selectByParent(r.getId());
        boolean any = lines.stream().anyMatch(l -> l.getOrderedQty().signum() > 0);
        boolean done = lines.stream().allMatch(l -> !OPEN.equals(l.getLineStatus()));
        if (r.getStatus() == DocStatus.COMPLETED && !done) support.fire(PurStateMachines.EXECUTABLE, mapper, r, BIZ_TYPE, PurAction.REOPEN, null);
        if (r.getStatus() == DocStatus.APPROVED && any) support.fire(PurStateMachines.EXECUTABLE, mapper, r, BIZ_TYPE, PurAction.START, null);
        if (r.getStatus() == DocStatus.IN_PROGRESS && done) support.fire(PurStateMachines.EXECUTABLE, mapper, r, BIZ_TYPE, PurAction.COMPLETE, null);
        else if (r.getStatus() == DocStatus.IN_PROGRESS && !any) support.fire(PurStateMachines.EXECUTABLE, mapper, r, BIZ_TYPE, PurAction.REOPEN, null);
    }

    /** 转订单前校验申请行可转（已审核/执行中申请的未关闭行） */
    public List<RequisitionLineDO> pendingLines(Collection<Long> lineIds) {
        List<RequisitionLineDO> lines = lineMapper.selectBatchIds(lineIds);
        Map<Long, RequisitionDO> heads = mapper.selectBatchIds(lines.stream().map(RequisitionLineDO::getRequisitionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(RequisitionDO::getId, r -> r));
        for (RequisitionLineDO l : lines) {
            RequisitionDO r = heads.get(l.getRequisitionId());
            if (r == null || (r.getStatus() != DocStatus.APPROVED && r.getStatus() != DocStatus.IN_PROGRESS) || !OPEN.equals(l.getLineStatus())) {
                throw BizException.of(PurchaseErrorCodes.REQ_LINE_NOT_PENDING, r == null ? "" : r.getDocNo(), l.getLineNo());
            }
        }
        return lines;
    }

    public Map<Long, RequisitionDO> heads(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Collections.emptyMap();
        return mapper.selectBatchIds(set).stream().collect(Collectors.toMap(RequisitionDO::getId, r -> r));
    }

    public Map<Long, RequisitionLineDO> lines(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Collections.emptyMap();
        return lineMapper.selectBatchIds(set).stream().collect(Collectors.toMap(RequisitionLineDO::getId, l -> l));
    }

    // ==================== MRP（R02） ====================

    /** 按计划员合并为一张 MRP 类型申请单；参数控制是否自动提交 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createFromMrp(List<MrpPurchaseSuggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) return List.of();
        Map<Long, List<MrpPurchaseSuggestion>> byPlanner = new LinkedHashMap<>();
        for (MrpPurchaseSuggestion s : suggestions) {
            byPlanner.computeIfAbsent(s.plannerId() != null ? s.plannerId() : Objects.requireNonNullElse(support.currentUser(), 0L), k -> new ArrayList<>()).add(s);
        }
        boolean autoSubmit = support.params().getBool(PurchaseModuleConfig.P_MRP_AUTO_SUBMIT);
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, List<MrpPurchaseSuggestion>> e : byPlanner.entrySet()) {
            RequisitionDO r = new RequisitionDO();
            r.setDocNo(support.nextNo(BIZ_TYPE));
            r.setDocDate(LocalDate.now());
            r.setStatus(DocStatus.DRAFT);
            r.setRequisitionType("MRP");
            support.fillOwner(r, e.getKey() == 0L ? null : e.getKey());
            r.setRequestDeptId(r.getDeptId());
            r.setUrgent(false);
            r.setMrpRunId(e.getValue().get(0).mrpRunId());
            r.setRemark("MRP 采购建议");
            mapper.insert(r);
            int no = 0;
            for (MrpPurchaseSuggestion s : e.getValue()) {
                MaterialDTO m = support.material(s.materialId());
                RequisitionLineDO d = new RequisitionLineDO();
                d.setRequisitionId(r.getId());
                d.setLineNo(++no);
                BigDecimal qty = s.qty();
                fillLine(d, m, m.baseUom(), qty, s.requiredDate() == null ? LocalDate.now() : s.requiredDate(), s.supplierId());
                d.setMrpResultId(s.mrpResultId());
                d.setSourceDemand(PurSupport.trim(s.sourceDemand()));
                lineMapper.insert(d);
            }
            support.log(BIZ_TYPE, r.getId(), r.getDocNo(), "CREATE", "MRP 生成", null, DocStatus.DRAFT.name(), null);
            if (autoSubmit) submit(r);
            ids.add(r.getId());
        }
        return ids;
    }

    public boolean materialIsActive(Long materialId) {
        return support.material(materialId).status() == MaterialStatus.ENABLED;
    }

    public RequisitionDO getOrThrow(Long id) {
        RequisitionDO r = id == null ? null : mapper.selectById(id);
        if (r == null) throw new BizException(PurchaseErrorCodes.REQ_NOT_EXISTS);
        return r;
    }
}
