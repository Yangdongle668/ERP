package com.erp.module.sales.service.order;

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
import com.erp.module.crm.api.credit.CreditApi;
import com.erp.module.crm.api.credit.CreditCheckPoint;
import com.erp.module.crm.api.credit.CreditCheckResult;
import com.erp.module.crm.api.customer.AddressDTO;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.sales.api.SalesErrorCodes;
import com.erp.module.sales.api.order.SalesOrderChangedEvent;
import com.erp.module.sales.api.price.SalesPriceDTO;
import com.erp.module.sales.config.SalesModuleConfig;
import com.erp.module.sales.controller.vo.CommonVOs.DocResult;
import com.erp.module.sales.controller.vo.OrderChangeVOs.ChangeDetail;
import com.erp.module.sales.controller.vo.OrderChangeVOs.ChangeLineResp;
import com.erp.module.sales.controller.vo.OrderChangeVOs.ChangeQuery;
import com.erp.module.sales.controller.vo.OrderChangeVOs.ChangeRow;
import com.erp.module.sales.controller.vo.OrderChangeVOs.ChangeSave;
import com.erp.module.sales.controller.vo.OrderChangeVOs.HeaderChange;
import com.erp.module.sales.controller.vo.OrderChangeVOs.HeaderSave;
import com.erp.module.sales.controller.vo.OrderChangeVOs.LineSave;
import com.erp.module.sales.dal.dataobject.SalOrderChangeDO;
import com.erp.module.sales.dal.dataobject.SalOrderChangeLineDO;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import com.erp.module.sales.dal.dataobject.SalOrderSnapshotDO;
import com.erp.module.sales.dal.mapper.SalOrderChangeLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderChangeMapper;
import com.erp.module.sales.dal.mapper.SalOrderLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.dal.mapper.SalOrderSnapshotMapper;
import com.erp.module.sales.service.CostService;
import com.erp.module.sales.service.SalAction;
import com.erp.module.sales.service.SalStateMachines;
import com.erp.module.sales.service.SalSupport;
import com.erp.module.sales.service.forecast.ForecastService;
import com.erp.module.sales.service.price.PriceLookupService;
import com.erp.module.system.api.paymentterm.PaymentTermApi;
import com.erp.module.system.api.paymentterm.PaymentTermDTO;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 销售订单变更（需求 04-04）：草稿 → 待审批 → 已审核（已应用到订单）；草稿可作废。
 * 审核时保存旧版本快照、应用变更、版本 + 1，并重算金额、毛利、回款计划和预测冲销。
 */
@Service("salOrderChangeService")
public class OrderChangeService {

    public static final String BIZ_TYPE = SalesModuleConfig.ORDER_CHANGE;
    static final Set<String> TYPES = Set.of("ADD", "MODIFY", "CANCEL");

    private final SalOrderChangeMapper mapper;
    private final SalOrderChangeLineMapper lineMapper;
    private final SalOrderMapper orderMapper;
    private final SalOrderLineMapper orderLineMapper;
    private final SalOrderSnapshotMapper snapshotMapper;
    private final OrderService orderService;
    private final OrderExecService execService;
    private final PaymentPlanService paymentPlanService;
    private final ForecastService forecastService;
    private final PriceLookupService priceLookupService;
    private final CostService costService;
    private final SalSupport support;
    private final CreditApi creditApi;
    private final PaymentTermApi paymentTermApi;
    private final WorkflowApi workflowApi;
    private final DomainEventPublisher eventPublisher;

    public OrderChangeService(SalOrderChangeMapper mapper, SalOrderChangeLineMapper lineMapper, SalOrderMapper orderMapper, SalOrderLineMapper orderLineMapper,
                              SalOrderSnapshotMapper snapshotMapper, OrderService orderService, OrderExecService execService,
                              PaymentPlanService paymentPlanService, ForecastService forecastService, PriceLookupService priceLookupService,
                              CostService costService, SalSupport support, CreditApi creditApi, PaymentTermApi paymentTermApi, WorkflowApi workflowApi,
                              DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.orderMapper = orderMapper;
        this.orderLineMapper = orderLineMapper;
        this.snapshotMapper = snapshotMapper;
        this.orderService = orderService;
        this.execService = execService;
        this.paymentPlanService = paymentPlanService;
        this.forecastService = forecastService;
        this.priceLookupService = priceLookupService;
        this.costService = costService;
        this.support = support;
        this.creditApi = creditApi;
        this.paymentTermApi = paymentTermApi;
        this.workflowApi = workflowApi;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 查询 ====================

    public PageResult<ChangeRow> page(ChangeQuery q) {
        LambdaQueryWrapper<SalOrderChangeDO> w = new LambdaQueryWrapper<SalOrderChangeDO>().eq(SalOrderChangeDO::getDeleted, false)
                .likeRight(StringUtils.hasText(q.getDocNo()), SalOrderChangeDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(StringUtils.hasText(q.getChangeReason()), SalOrderChangeDO::getChangeReason, q.getChangeReason())
                .ge(q.getDateFrom() != null, SalOrderChangeDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, SalOrderChangeDO::getDocDate, q.getDateTo());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(SalOrderChangeDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        StringBuilder cond = new StringBuilder();
        if (StringUtils.hasText(q.getOrderNo())) cond.append(" AND doc_no LIKE '").append(q.getOrderNo().trim().toUpperCase().replace("'", "")).append("%'");
        if (q.getCustomerId() != null) cond.append(" AND customer_id = ").append(q.getCustomerId().longValue());
        if (!cond.isEmpty()) w.inSql(SalOrderChangeDO::getOrderId, "SELECT id FROM sal_order WHERE deleted = 0" + cond);
        w.orderByDesc(SalOrderChangeDO::getId);
        IPage<SalOrderChangeDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        List<SalOrderChangeDO> list = page.getRecords();
        Map<Long, SalOrderDO> orders = orderService.byIds(list.stream().map(SalOrderChangeDO::getOrderId).toList());
        Map<Long, CustomerDTO> cs = support.customers(orders.values().stream().map(SalOrderDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(SalOrderChangeDO::getOwnerId).toList());
        return new PageResult<>(list.stream().map(c -> {
            SalOrderDO o = orders.get(c.getOrderId());
            return new ChangeRow(c.getId(), c.getDocNo(), c.getOrderId(), o == null ? null : o.getDocNo(), o == null ? null : o.getCustomerId(),
                    o == null ? null : SalSupport.shortName(cs, o.getCustomerId()), c.getOrderVersionFrom(), c.getOrderVersionFrom() + 1,
                    c.getChangeReason(), o == null ? null : o.getCurrency(), c.getAmountAfter().subtract(c.getAmountBefore()), c.getAmountChangeBase(),
                    c.getStatus().name(), SalSupport.name(users, c.getOwnerId()), c.getDocDate());
        }).toList(), page.getTotal());
    }

    public ChangeDetail detail(Long id) {
        SalOrderChangeDO c = getOrThrow(id);
        DataScopes.check(c.getOrgId(), c.getDeptId(), c.getOwnerId(), "订单变更单");
        SalOrderDO o = orderService.getOrThrow(c.getOrderId());
        CustomerDTO customer = support.customer(o.getCustomerId());
        List<SalOrderChangeLineDO> lines = lineMapper.selectByParent(id);
        List<SalOrderLineDO> orderLines = orderLineMapper.selectByParent(o.getId());
        Map<Long, Integer> lineNos = orderLines.stream().collect(Collectors.toMap(SalOrderLineDO::getId, SalOrderLineDO::getLineNo));
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalOrderChangeLineDO::getMaterialId).toList());
        List<HeaderChange> hc = headerChanges(c);
        Map<Long, UserDTO> users = support.users(List.of(SalSupport.nz(c.getOwnerId()), SalSupport.nz(c.getCreatedBy())));
        return new ChangeDetail(c.getId(), c.getDocNo(), c.getDocDate(), c.getStatus().name(), o.getId(), o.getDocNo(), o.getOrderVersion(),
                o.getCustomerId(), customer.shortName(), o.getCurrency(), Boolean.TRUE.equals(o.getTaxIncluded()), c.getOrderVersionFrom(),
                c.getChangeReason(), c.getReasonRemark(), c.getAmountBefore(), c.getAmountAfter(), c.getAmountChangeBase(), hc, headerAfter(o, hc),
                c.getOwnerId(), SalSupport.name(users, c.getOwnerId()), SalSupport.name(users, c.getCreatedBy()), c.getCreatedAt(), c.getVersion(),
                lines.stream().map(l -> {
                    MaterialDTO m = ms.get(l.getMaterialId());
                    return new ChangeLineResp(l.getId(), l.getLineNo(), l.getChangeType(), l.getOrderLineId(),
                            l.getOrderLineId() == null ? null : lineNos.get(l.getOrderLineId()), l.getMaterialId(), m == null ? null : m.code(),
                            m == null ? null : m.name(), m == null ? null : m.spec(), l.getUom(), l.getOldQty(), l.getNewQty(), l.getOldPrice(),
                            l.getNewPrice(), l.getOldRequiredDate(), l.getNewRequiredDate(), l.getNewCustomerPartNo(), l.getNewDescription(), l.getRemark());
                }).toList(), orderService.lineResps(orderLines, SalSupport.canViewOrderCost()));
    }

    List<HeaderChange> headerChanges(SalOrderChangeDO c) {
        if (!StringUtils.hasText(c.getHeaderChanges())) return List.of();
        try {
            return support.objectMapper().readValue(c.getHeaderChanges(), new TypeReference<List<HeaderChange>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 变更后的单头（未变更的字段为订单当前值） */
    private HeaderSave headerAfter(SalOrderDO o, List<HeaderChange> hc) {
        Map<String, String> v = currentHeader(o);
        hc.forEach(h -> v.put(h.field(), h.newValue()));
        return new HeaderSave(v.get("customerPoNo"), toLong(v.get("paymentTermId")), v.get("tradeTerm"), v.get("portOfLoading"),
                v.get("portOfDestination"), toLong(v.get("shipToAddressId")), toLong(v.get("contactId")), v.get("terms"), v.get("remark"));
    }

    private static Long toLong(String s) {
        return StringUtils.hasText(s) ? Long.valueOf(s) : null;
    }

    static final Map<String, String> HEADER_LABELS = new LinkedHashMap<>();

    static {
        HEADER_LABELS.put("customerPoNo", "客户 PO 号");
        HEADER_LABELS.put("paymentTermId", "付款条件");
        HEADER_LABELS.put("tradeTerm", "贸易条款");
        HEADER_LABELS.put("portOfLoading", "起运港");
        HEADER_LABELS.put("portOfDestination", "目的港");
        HEADER_LABELS.put("shipToAddressId", "收货地址");
        HEADER_LABELS.put("contactId", "联系人");
        HEADER_LABELS.put("terms", "合同条款");
        HEADER_LABELS.put("remark", "备注");
    }

    private static Map<String, String> currentHeader(SalOrderDO o) {
        Map<String, String> v = new HashMap<>();
        v.put("customerPoNo", o.getCustomerPoNo());
        v.put("paymentTermId", o.getPaymentTermId() == null ? null : o.getPaymentTermId().toString());
        v.put("tradeTerm", o.getTradeTerm());
        v.put("portOfLoading", o.getPortOfLoading());
        v.put("portOfDestination", o.getPortOfDestination());
        v.put("shipToAddressId", o.getShipToAddressId() == null ? null : o.getShipToAddressId().toString());
        v.put("contactId", o.getContactId() == null ? null : o.getContactId().toString());
        v.put("terms", o.getTerms());
        v.put("remark", o.getRemark());
        return v;
    }

    // ==================== 编辑 ====================

    /** 从订单新建变更单（R01）：订单为已审核 / 执行中，且没有未完成的变更单 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long orderId) {
        SalOrderDO o = orderService.getOrThrow(orderId);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        if (!OrderService.ACTIVE.contains(o.getStatus())) throw new BizException(SalesErrorCodes.CHANGE_ORDER_STATUS);
        orderService.checkNoRunningChange(orderId);
        SalOrderChangeDO c = new SalOrderChangeDO();
        c.setDocNo(support.nextNo(BIZ_TYPE));
        c.setDocDate(LocalDate.now());
        c.setStatus(DocStatus.DRAFT);
        support.fillOwner(c, o.getOwnerId());
        c.setOrderId(orderId);
        c.setOrderVersionFrom(o.getOrderVersion());
        c.setChangeReason("CUSTOMER");
        c.setReasonRemark("");
        c.setAmountBefore(o.getTotalAmount());
        c.setAmountAfter(o.getTotalAmount());
        c.setAmountChangeBase(BigDecimal.ZERO);
        c.setSourceType(OrderService.BIZ_TYPE);
        c.setSourceId(orderId);
        c.setSourceNo(o.getDocNo());
        mapper.insert(c);
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ChangeSave req) {
        SalOrderChangeDO c = getOrThrow(id);
        DataScopes.check(c.getOrgId(), c.getDeptId(), c.getOwnerId(), "订单变更单");
        SalSupport.requireDraft(c);
        if (req.version() != null) c.setVersion(req.version());
        SalOrderDO o = orderService.getOrThrow(c.getOrderId());
        support.dict().validate("sal_change_reason", req.changeReason(), "变更原因");
        c.setChangeReason(req.changeReason());
        c.setReasonRemark(req.reasonRemark().trim());
        c.setHeaderChanges(support.json(diffHeader(o, req.header())));
        saveLines(c, o, req.lines());
        recalcAmounts(c, o);
        mapper.updateByIdOrFail(c);
    }

    /** 单头：与订单当前值比较得出变更项（币别、客户、订单类型不可变更） */
    private List<HeaderChange> diffHeader(SalOrderDO o, HeaderSave h) {
        if (h == null) return List.of();
        Map<String, String> cur = currentHeader(o);
        Map<String, String> next = new HashMap<>();
        next.put("customerPoNo", SalSupport.trim(h.customerPoNo()));
        next.put("paymentTermId", h.paymentTermId() == null ? null : h.paymentTermId().toString());
        next.put("tradeTerm", SalSupport.trim(h.tradeTerm()));
        next.put("portOfLoading", SalSupport.trim(h.portOfLoading()));
        next.put("portOfDestination", SalSupport.trim(h.portOfDestination()));
        next.put("shipToAddressId", h.shipToAddressId() == null ? null : h.shipToAddressId().toString());
        next.put("contactId", h.contactId() == null ? null : h.contactId().toString());
        next.put("terms", SalSupport.trim(h.terms()));
        next.put("remark", SalSupport.trim(h.remark()));
        List<HeaderChange> list = new ArrayList<>();
        for (Map.Entry<String, String> e : HEADER_LABELS.entrySet()) {
            String f = e.getKey();
            if (Objects.equals(cur.get(f), next.get(f))) continue;
            switch (f) {
                case "paymentTermId" -> {
                    if (next.get(f) == null) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "请选择付款条件");
                    paymentTermApi.validate(Long.valueOf(next.get(f)), "SALES");
                }
                case "tradeTerm" -> {
                    if (next.get(f) != null) support.dict().validate("sys_trade_term", next.get(f), "贸易条款");
                }
                case "shipToAddressId" -> {
                    if (next.get(f) == null) throw new BizException(SalesErrorCodes.ORDER_SHIP_ADDRESS_REQUIRED);
                    Long aid = Long.valueOf(next.get(f));
                    if (support.customerApi().getAddresses(o.getCustomerId(), null).stream().noneMatch(a -> a.id().equals(aid))) {
                        throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "收货地址");
                    }
                }
                case "contactId" -> {
                    if (next.get(f) != null) {
                        Long cid = Long.valueOf(next.get(f));
                        if (support.customerApi().getContacts(o.getCustomerId()).stream().noneMatch(x -> x.id().equals(cid))) {
                            throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "联系人");
                        }
                    }
                }
                case "customerPoNo" -> {
                    String po = next.get(f);
                    if (po != null) {
                        SalOrderDO dup = orderMapper.selectOne(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getCustomerId, o.getCustomerId())
                                .eq(SalOrderDO::getCustomerPoNo, po).ne(SalOrderDO::getStatus, DocStatus.VOIDED).ne(SalOrderDO::getId, o.getId()).last("LIMIT 1"));
                        if (dup != null) throw BizException.of(SalesErrorCodes.ORDER_PO_DUPLICATE, po, dup.getDocNo());
                    }
                }
                default -> {
                }
            }
            list.add(new HeaderChange(f, e.getValue(), cur.get(f), next.get(f)));
        }
        return list;
    }

    /** 明细（R02、R03）：只保存有变化的行 */
    private void saveLines(SalOrderChangeDO c, SalOrderDO o, List<LineSave> lines) {
        lineMapper.deleteByParent(c.getId());
        if (lines == null || lines.isEmpty()) return;
        Map<Long, SalOrderLineDO> orderLines = orderLineMapper.selectByParent(o.getId()).stream().collect(Collectors.toMap(SalOrderLineDO::getId, l -> l));
        boolean incl = Boolean.TRUE.equals(o.getTaxIncluded());
        LocalDate today = LocalDate.now();
        Set<Long> seen = new HashSet<>();
        int no = 0;
        for (LineSave s : lines) {
            if (!TYPES.contains(s.changeType())) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "变更类型");
            SalOrderChangeLineDO d = new SalOrderChangeLineDO();
            d.setChangeId(c.getId());
            d.setChangeType(s.changeType());
            d.setRemark(SalSupport.trim(s.remark()));
            if ("ADD".equals(s.changeType())) {
                MaterialDTO m = s.materialId() == null ? null : support.materialApi().getMaterial(s.materialId()).orElse(null);
                int n = no + 1;
                if (m == null) throw BizException.of(SalesErrorCodes.LINE_FIELD_REQUIRED, "新增 " + n, "物料");
                if (s.newQty() == null || s.newQty().signum() <= 0) throw BizException.of(SalesErrorCodes.LINE_QTY_POSITIVE, "新增 " + n);
                if (s.newRequiredDate() == null) throw BizException.of(SalesErrorCodes.LINE_FIELD_REQUIRED, "新增 " + n, "要求交期");
                if (s.newRequiredDate().isBefore(today)) throw BizException.of(SalesErrorCodes.CHANGE_REQUIRED_PAST, "新增 " + n);
                d.setMaterialId(m.id());
                d.setUom(StringUtils.hasText(s.uom()) ? s.uom().trim() : m.baseUom());
                support.toBase(m.id(), BigDecimal.ONE, d.getUom());
                d.setNewQty(Decimals.qty(s.newQty()));
                BigDecimal price = s.newPrice();
                if (price == null) {
                    Optional<SalesPriceDTO> p = priceLookupService.getPrice(o.getCustomerId(), m.id(), d.getNewQty(), d.getUom(), today, o.getCurrency());
                    price = p.map(x -> PriceLookupService.convert(x, incl, orderService.taxRate(support.customer(o.getCustomerId()))))
                            .orElse(BigDecimal.ZERO);
                }
                if (price.signum() < 0) throw BizException.of(SalesErrorCodes.LINE_PRICE_NEGATIVE, "新增 " + n);
                d.setNewPrice(Decimals.price(price));
                d.setNewRequiredDate(s.newRequiredDate());
                d.setNewCustomerPartNo(SalSupport.trim(s.newCustomerPartNo()));
                d.setNewDescription(SalSupport.trim(s.newDescription()));
            } else {
                SalOrderLineDO ol = s.orderLineId() == null ? null : orderLines.get(s.orderLineId());
                if (ol == null || OrderService.CLOSED.equals(ol.getLineStatus())) {
                    throw BizException.of(SalesErrorCodes.CHANGE_LINE_INVALID, ol == null ? no + 1 : ol.getLineNo());
                }
                if (!seen.add(ol.getId())) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "第 " + ol.getLineNo() + " 行重复变更");
                d.setOrderLineId(ol.getId());
                d.setMaterialId(ol.getMaterialId());
                d.setUom(ol.getUom());
                d.setOldQty(ol.getQty());
                BigDecimal oldPrice = incl ? ol.getPriceInclTax() : ol.getPrice();
                d.setOldPrice(oldPrice);
                d.setOldRequiredDate(ol.getRequiredDate());
                if ("CANCEL".equals(s.changeType())) {
                    if (ol.getNoticedQty().signum() > 0) throw BizException.of(SalesErrorCodes.CHANGE_CANCEL_NOTICED, ol.getLineNo());
                    d.setNewQty(BigDecimal.ZERO);
                } else {
                    BigDecimal newQty = s.newQty() == null ? ol.getQty() : Decimals.qty(s.newQty());
                    if (newQty.signum() <= 0) throw BizException.of(SalesErrorCodes.LINE_QTY_POSITIVE, ol.getLineNo());
                    BigDecimal newBase = Decimals.qty(support.toBase(ol.getMaterialId(), newQty, ol.getUom()));
                    if (newBase.compareTo(ol.getNoticedQty()) < 0) {
                        throw BizException.of(SalesErrorCodes.CHANGE_QTY_BELOW_NOTICED, ol.getLineNo(), SalSupport.plain(ol.getNoticedQty()));
                    }
                    BigDecimal newPrice = s.newPrice() == null ? oldPrice : Decimals.price(s.newPrice());
                    if (newPrice.signum() < 0) throw BizException.of(SalesErrorCodes.LINE_PRICE_NEGATIVE, ol.getLineNo());
                    if (newPrice.compareTo(oldPrice) != 0 && ol.getInvoicedQty().signum() > 0) {
                        throw BizException.of(SalesErrorCodes.CHANGE_INVOICED_PRICE, ol.getLineNo());
                    }
                    LocalDate newDate = s.newRequiredDate() == null ? ol.getRequiredDate() : s.newRequiredDate();
                    if (!newDate.equals(ol.getRequiredDate()) && newDate.isBefore(today)) {
                        throw BizException.of(SalesErrorCodes.CHANGE_REQUIRED_PAST, ol.getLineNo());
                    }
                    String part = SalSupport.trim(s.newCustomerPartNo());
                    String desc = SalSupport.trim(s.newDescription());
                    boolean changed = newQty.compareTo(ol.getQty()) != 0 || newPrice.compareTo(oldPrice) != 0 || !newDate.equals(ol.getRequiredDate())
                            || part != null && !part.equals(ol.getCustomerPartNo()) || desc != null && !desc.equals(ol.getDescription());
                    if (!changed) continue;
                    d.setNewQty(newQty);
                    d.setNewPrice(newPrice);
                    d.setNewRequiredDate(newDate);
                    d.setNewCustomerPartNo(part);
                    d.setNewDescription(desc);
                }
            }
            no++;
            d.setLineNo(no);
            lineMapper.insert(d);
        }
    }

    /** 变更前后价税合计与本位币差额 */
    private void recalcAmounts(SalOrderChangeDO c, SalOrderDO o) {
        c.setAmountBefore(o.getTotalAmount());
        BigDecimal after = o.getTotalAmount();
        boolean incl = Boolean.TRUE.equals(o.getTaxIncluded());
        Map<Long, SalOrderLineDO> ols = orderLineMapper.selectByParent(o.getId()).stream().collect(Collectors.toMap(SalOrderLineDO::getId, l -> l));
        BigDecimal defaultTax = orderService.taxRate(support.customer(o.getCustomerId()));
        for (SalOrderChangeLineDO l : lineMapper.selectByParent(c.getId())) {
            SalOrderLineDO ol = l.getOrderLineId() == null ? null : ols.get(l.getOrderLineId());
            SalOrderLineDO sim = new SalOrderLineDO();
            sim.setTaxRate(ol == null ? defaultTax : ol.getTaxRate());
            sim.setQty(l.getNewQty());
            OrderService.computeAmounts(sim, incl, l.getNewPrice() == null ? BigDecimal.ZERO : l.getNewPrice());
            after = after.add(sim.getTotalAmount()).subtract(ol == null ? BigDecimal.ZERO : ol.getTotalAmount());
        }
        c.setAmountAfter(after);
        c.setAmountChangeBase(support.currencyApi().toBase(after.subtract(c.getAmountBefore()), o.getExchangeRate()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SalOrderChangeDO c = getOrThrow(id);
        DataScopes.check(c.getOrgId(), c.getDeptId(), c.getOwnerId(), "订单变更单");
        SalSupport.requireDraft(c);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        SalOrderChangeDO c = getOrThrow(id);
        DataScopes.check(c.getOrgId(), c.getDeptId(), c.getOwnerId(), "订单变更单");
        support.fire(SalStateMachines.SIMPLE, mapper, c, BIZ_TYPE, SalAction.VOID, SalSupport.requireReason(reason, "作废"));
    }

    // ==================== 提交 / 审核 ====================

    /** 提交：至少一项变更；金额增加时对增加部分做信用检查（R07，WARN 需确认） */
    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id, boolean confirmCredit) {
        SalOrderChangeDO c = getOrThrow(id);
        DataScopes.check(c.getOrgId(), c.getDeptId(), c.getOwnerId(), "订单变更单");
        SalSupport.requireDraft(c);
        if (!StringUtils.hasText(c.getReasonRemark())) throw BizException.of(SalesErrorCodes.REASON_REQUIRED, "变更");
        SalOrderDO o = orderService.getOrThrow(c.getOrderId());
        if (!OrderService.ACTIVE.contains(o.getStatus())) throw new BizException(SalesErrorCodes.CHANGE_ORDER_STATUS);
        if (!o.getOrderVersion().equals(c.getOrderVersionFrom())) throw new BizException(SalesErrorCodes.CHANGE_ORDER_MODIFIED);
        List<SalOrderChangeLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty() && headerChanges(c).isEmpty()) throw new BizException(SalesErrorCodes.CHANGE_NOTHING);
        recalcAmounts(c, o);
        List<String> warnings = new ArrayList<>();
        if (c.getAmountChangeBase().signum() > 0 && !OrderService.REPLACEMENT.equals(o.getOrderType())) {
            CreditCheckResult cr = creditApi.check(o.getCustomerId(), c.getAmountChangeBase(), CreditCheckPoint.ORDER);
            if (!cr.pass()) throw BizException.of(SalesErrorCodes.ORDER_CREDIT_BLOCKED, cr.message());
            if (cr.message() != null && "WARN".equals(cr.mode())) {
                if (!confirmCredit) {
                    throw BizException.of(SalesErrorCodes.ORDER_CREDIT_CONFIRM, cr.message()).withData(Map.of("needConfirm", true, "message", cr.message()));
                }
                warnings.add(cr.message());
            }
        }
        mapper.updateByIdOrFail(c);
        support.fire(SalStateMachines.SIMPLE, mapper, c, BIZ_TYPE, SalAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountChangeBase", c.getAmountChangeBase());
        vars.put("changeReason", c.getChangeReason());
        Map<String, Long> users = new HashMap<>();
        users.put("ownerId", o.getOwnerId());
        StartResult r = workflowApi.start(BIZ_TYPE, id, c.getDocNo(), "订单变更 " + c.getDocNo() + "（" + o.getDocNo() + "）", vars, users,
                support.currentUser());
        if (!r.isStarted()) approve(c);
        return new DocResult(c.getStatus().name(), warnings);
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        SalOrderChangeDO c = getOrThrow(e.getBizId());
        if (c.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> approve(c);
            case WITHDRAWN -> support.fire(SalStateMachines.SIMPLE, mapper, c, BIZ_TYPE, SalAction.WITHDRAW, null);
            default -> support.fire(SalStateMachines.SIMPLE, mapper, c, BIZ_TYPE, SalAction.REJECT, e.getComment());
        }
    }

    /** R04、R05：检查版本 → 保存快照 → 应用变更 → 版本 + 1 → 重算金额、毛利、回款计划、预测冲销 → 发布事件 */
    private void approve(SalOrderChangeDO c) {
        SalOrderDO o = orderService.getOrThrow(c.getOrderId());
        if (!o.getOrderVersion().equals(c.getOrderVersionFrom())) throw new BizException(SalesErrorCodes.CHANGE_ORDER_MODIFIED);
        if (!OrderService.ACTIVE.contains(o.getStatus())) throw new BizException(SalesErrorCodes.CHANGE_ORDER_STATUS);
        List<SalOrderLineDO> before = orderLineMapper.selectByParent(o.getId());
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("header", o);
        snap.put("lines", before);
        SalOrderSnapshotDO s = new SalOrderSnapshotDO();
        s.setOrderId(o.getId());
        s.setOrderVersion(o.getOrderVersion());
        s.setContent(support.json(snap));
        s.setChangeId(c.getId());
        snapshotMapper.insert(s);

        applyHeader(o, headerChanges(c));
        CustomerDTO customer = support.customer(o.getCustomerId());
        Map<Long, SalOrderLineDO> byId = before.stream().collect(Collectors.toMap(SalOrderLineDO::getId, l -> l));
        boolean incl = Boolean.TRUE.equals(o.getTaxIncluded());
        List<SalOrderChangeLineDO> changes = lineMapper.selectByParent(c.getId());
        Map<Long, CostService.UnitCost> costs = costService.unitCosts(changes.stream().map(SalOrderChangeLineDO::getMaterialId).toList());
        BigDecimal minMargin = costService.minMarginRate();
        int nextNo = before.stream().mapToInt(SalOrderLineDO::getLineNo).max().orElse(0);
        List<SalesOrderChangedEvent.LineChange> events = new ArrayList<>();
        Set<Long> consumed = new HashSet<>();
        for (SalOrderChangeLineDO ch : changes) {
            SalOrderLineDO l;
            BigDecimal oldBase = null;
            LocalDate oldDate = null;
            if ("ADD".equals(ch.getChangeType())) {
                MaterialDTO m = support.material(ch.getMaterialId());
                l = new SalOrderLineDO();
                l.setOrderId(o.getId());
                l.setLineNo(++nextNo);
                l.setMaterialId(m.id());
                l.setUom(ch.getUom());
                l.setTaxRate(orderService.taxRate(customer));
                l.setCustomerPartNo(ch.getNewCustomerPartNo());
                l.setDescription(ch.getNewDescription() != null ? ch.getNewDescription() : OrderService.defaultDescription(m, customer.foreign()));
                l.setPriceSource("订单变更 " + c.getDocNo());
                OrderService.initQty(l);
            } else {
                l = byId.get(ch.getOrderLineId());
                oldBase = l.getBaseQty();
                oldDate = l.getRequiredDate();
            }
            if ("CANCEL".equals(ch.getChangeType())) {
                l.setQty(BigDecimal.ZERO);
                l.setBaseQty(BigDecimal.ZERO);
                OrderService.computeAmounts(l, incl, incl ? l.getPriceInclTax() : l.getPrice());
                l.setLineStatus(OrderService.CLOSED);
            } else {
                l.setQty(ch.getNewQty());
                l.setBaseQty(Decimals.qty(support.toBase(l.getMaterialId(), l.getQty(), l.getUom())));
                l.setRequiredDate(ch.getNewRequiredDate());
                if (ch.getNewCustomerPartNo() != null) l.setCustomerPartNo(ch.getNewCustomerPartNo());
                if (ch.getNewDescription() != null) l.setDescription(ch.getNewDescription());
                OrderService.computeAmounts(l, incl, ch.getNewPrice());
                if (!OrderService.REPLACEMENT.equals(o.getOrderType())) {
                    CostService.UnitCost uc = costs.get(l.getMaterialId());
                    CostService.Margin mg = costService.margin(l.getPrice(), o.getExchangeRate(), OrderService.basePerUom(l),
                            uc == null ? null : uc.cost(), minMargin);
                    l.setCostPrice(mg.cost());
                    l.setMarginRate(mg.marginRate());
                    l.setBelowFloor(mg.belowFloor());
                } else {
                    l.setBelowFloor(false);
                }
                if (!OrderService.CLOSED.equals(l.getLineStatus())) {
                    l.setLineStatus(l.getShippedQty().compareTo(l.getBaseQty()) >= 0 && l.getBaseQty().signum() > 0 ? OrderService.SHIPPED : OrderService.OPEN);
                }
            }
            if (l.getId() == null) orderLineMapper.insert(l);
            else orderLineMapper.updateByIdOrFail(l);
            boolean monthChanged = oldDate != null && !YearMonth.from(oldDate).equals(YearMonth.from(l.getRequiredDate()));
            if (monthChanged) forecastService.resync(o, l, l.getBaseQty(), consumed);
            else forecastService.sync(o, l, l.getBaseQty(), consumed);
            events.add(new SalesOrderChangedEvent.LineChange(l.getId(), l.getMaterialId(), ch.getChangeType(), oldBase, l.getBaseQty(), oldDate,
                    l.getRequiredDate()));
        }
        int from = o.getOrderVersion();
        o.setOrderVersion(from + 1);
        orderService.recalcTotals(o);
        orderMapper.updateByIdOrFail(o);
        support.fire(SalStateMachines.SIMPLE, mapper, c, BIZ_TYPE, SalAction.APPROVE, null);
        support.log(OrderService.BIZ_TYPE, o.getId(), o.getDocNo(), "CHANGE", "订单变更", o.getStatus().name(), o.getStatus().name(),
                c.getDocNo() + "：V" + from + " → V" + o.getOrderVersion());
        paymentPlanService.recalc(o);
        paymentPlanService.refreshBeforeShipment(o);
        forecastService.publishConsumed(consumed);
        execService.refreshStatus(orderService.getOrThrow(o.getId()));
        eventPublisher.publish(new SalesOrderChangedEvent(o.getId(), o.getDocNo(), o.getOrderVersion(), c.getId(), c.getDocNo(), events,
                OrderService.lineInfos(orderLineMapper.selectByParent(o.getId()))));
        orderService.openAmountChanged(o);
    }

    private void applyHeader(SalOrderDO o, List<HeaderChange> changes) {
        Function<String, Long> id = s -> StringUtils.hasText(s) ? Long.valueOf(s) : null;
        for (HeaderChange h : changes) {
            switch (h.field()) {
                case "customerPoNo" -> o.setCustomerPoNo(h.newValue());
                case "paymentTermId" -> {
                    PaymentTermDTO term = paymentTermApi.validate(id.apply(h.newValue()), "SALES");
                    o.setPaymentTermId(term.id());
                    o.setPaymentTermSnapshot(support.json(term));
                }
                case "tradeTerm" -> o.setTradeTerm(h.newValue());
                case "portOfLoading" -> o.setPortOfLoading(h.newValue());
                case "portOfDestination" -> o.setPortOfDestination(h.newValue());
                case "shipToAddressId" -> {
                    Long aid = id.apply(h.newValue());
                    AddressDTO a = support.customerApi().getAddresses(o.getCustomerId(), null).stream().filter(x -> x.id().equals(aid)).findFirst()
                            .orElseThrow(() -> BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "收货地址"));
                    o.setShipToAddressId(aid);
                    o.setShipToSnapshot(support.json(a));
                }
                case "contactId" -> o.setContactId(id.apply(h.newValue()));
                case "terms" -> o.setTerms(h.newValue());
                case "remark" -> o.setRemark(h.newValue());
                default -> {
                }
            }
        }
    }

    public SalOrderChangeDO getOrThrow(Long id) {
        SalOrderChangeDO c = id == null ? null : mapper.selectById(id);
        if (c == null) throw new BizException(SalesErrorCodes.CHANGE_NOT_EXISTS);
        return c;
    }
}
