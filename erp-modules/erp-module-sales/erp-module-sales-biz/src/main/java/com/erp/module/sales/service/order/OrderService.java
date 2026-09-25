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
import com.erp.module.crm.api.customer.ContactDTO;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.crm.api.customer.CustomerStatus;
import com.erp.module.crm.api.opportunity.OpportunityApi;
import com.erp.module.crm.api.part.CustomerPartApi;
import com.erp.module.crm.api.part.CustomerPartDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPlanAttr;
import com.erp.module.sales.api.SalesErrorCodes;
import com.erp.module.sales.api.order.SalesOrderApprovedEvent;
import com.erp.module.sales.api.order.SalesOrderClosedEvent;
import com.erp.module.sales.api.order.SalesOrderLineInfo;
import com.erp.module.sales.api.order.SalesOrderOpenAmountChangedEvent;
import com.erp.module.sales.api.order.SalesOrderReferenceChecker;
import com.erp.module.sales.api.order.SalesOrderUnapprovedEvent;
import com.erp.module.sales.api.price.SalesPriceDTO;
import com.erp.module.sales.config.SalesModuleConfig;
import com.erp.module.sales.controller.vo.CommonVOs.DocResult;
import com.erp.module.sales.controller.vo.CommonVOs.RelatedDoc;
import com.erp.module.sales.controller.vo.CommonVOs.SaveResult;
import com.erp.module.sales.controller.vo.OrderVOs.AddressOption;
import com.erp.module.sales.controller.vo.OrderVOs.ContactOption;
import com.erp.module.sales.controller.vo.OrderVOs.CustomerDefaults;
import com.erp.module.sales.controller.vo.OrderVOs.ExecRow;
import com.erp.module.sales.controller.vo.OrderVOs.OrderDetail;
import com.erp.module.sales.controller.vo.OrderVOs.OrderLineResp;
import com.erp.module.sales.controller.vo.OrderVOs.OrderLineRow;
import com.erp.module.sales.controller.vo.OrderVOs.OrderLineSave;
import com.erp.module.sales.controller.vo.OrderVOs.OrderQuery;
import com.erp.module.sales.controller.vo.OrderVOs.OrderRow;
import com.erp.module.sales.controller.vo.OrderVOs.OrderSave;
import com.erp.module.sales.controller.vo.OrderVOs.SnapshotRow;
import com.erp.module.sales.dal.dataobject.SalOrderChangeDO;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalOrderExecDO;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import com.erp.module.sales.dal.dataobject.SalOrderSnapshotDO;
import com.erp.module.sales.dal.dataobject.SalQuotationDO;
import com.erp.module.sales.dal.dataobject.SalReturnDO;
import com.erp.module.sales.dal.dataobject.SalReturnLineDO;
import com.erp.module.sales.dal.mapper.SalOrderChangeMapper;
import com.erp.module.sales.dal.mapper.SalOrderExecMapper;
import com.erp.module.sales.dal.mapper.SalOrderLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.dal.mapper.SalOrderSnapshotMapper;
import com.erp.module.sales.dal.mapper.SalQuotationMapper;
import com.erp.module.sales.dal.mapper.SalReturnLineMapper;
import com.erp.module.sales.dal.mapper.SalReturnMapper;
import com.erp.module.sales.service.CostService;
import com.erp.module.sales.service.SalAction;
import com.erp.module.sales.service.SalStateMachines;
import com.erp.module.sales.service.SalSupport;
import com.erp.module.sales.service.forecast.ForecastService;
import com.erp.module.sales.service.price.PriceListService;
import com.erp.module.sales.service.price.PriceLookupService;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.paymentterm.PaymentTermApi;
import com.erp.module.system.api.paymentterm.PaymentTermDTO;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
 * 销售订单（需求 04-03）：草稿 → 待审批 → 已审核 → 执行中（首次出货通知 / 出货）→ 已完成 / 已关闭。
 * 执行数据回写见 {@link OrderExecService}；回款计划见 {@link PaymentPlanService}。
 */
@Service("salOrderService")
public class OrderService {

    public static final String BIZ_TYPE = SalesModuleConfig.ORDER;
    public static final String OPEN = "OPEN";
    public static final String SHIPPED = "SHIPPED";
    public static final String CLOSED = "CLOSED";
    public static final String REPLACEMENT = "REPLACEMENT";
    static final Set<String> TYPES = Set.of("NORMAL", "SAMPLE", REPLACEMENT, "STOCK");
    public static final List<DocStatus> ACTIVE = List.of(DocStatus.APPROVED, DocStatus.IN_PROGRESS);

    private final SalOrderMapper mapper;
    private final SalOrderLineMapper lineMapper;
    private final SalOrderExecMapper execMapper;
    private final SalOrderSnapshotMapper snapshotMapper;
    private final SalOrderChangeMapper changeMapper;
    private final SalQuotationMapper quotationMapper;
    private final SalReturnMapper returnMapper;
    private final SalReturnLineMapper returnLineMapper;
    private final PaymentPlanService paymentPlanService;
    private final ForecastService forecastService;
    private final PriceLookupService priceLookupService;
    private final PriceListService priceListService;
    private final CostService costService;
    private final SalSupport support;
    private final CustomerPartApi customerPartApi;
    private final CreditApi creditApi;
    private final OpportunityApi opportunityApi;
    private final PaymentTermApi paymentTermApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;
    private final List<SalesOrderReferenceChecker> referenceCheckers;

    public OrderService(SalOrderMapper mapper, SalOrderLineMapper lineMapper, SalOrderExecMapper execMapper, SalOrderSnapshotMapper snapshotMapper,
                        SalOrderChangeMapper changeMapper, SalQuotationMapper quotationMapper, SalReturnMapper returnMapper, SalReturnLineMapper returnLineMapper,
                        PaymentPlanService paymentPlanService, ForecastService forecastService, PriceLookupService priceLookupService,
                        PriceListService priceListService, CostService costService, SalSupport support, CustomerPartApi customerPartApi,
                        CreditApi creditApi, OpportunityApi opportunityApi, PaymentTermApi paymentTermApi, WorkflowApi workflowApi, FileApi fileApi,
                        DomainEventPublisher eventPublisher, List<SalesOrderReferenceChecker> referenceCheckers) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.execMapper = execMapper;
        this.snapshotMapper = snapshotMapper;
        this.changeMapper = changeMapper;
        this.quotationMapper = quotationMapper;
        this.returnMapper = returnMapper;
        this.returnLineMapper = returnLineMapper;
        this.paymentPlanService = paymentPlanService;
        this.forecastService = forecastService;
        this.priceLookupService = priceLookupService;
        this.priceListService = priceListService;
        this.costService = costService;
        this.support = support;
        this.customerPartApi = customerPartApi;
        this.creditApi = creditApi;
        this.opportunityApi = opportunityApi;
        this.paymentTermApi = paymentTermApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
        this.referenceCheckers = referenceCheckers;
    }

    // ==================== 查询 ====================

    public PageResult<OrderRow> page(OrderQuery q) {
        IPage<SalOrderDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), query(q));
        return new PageResult<>(rows(page.getRecords()), page.getTotal());
    }

    public List<OrderRow> listForExport(OrderQuery q, int limit) {
        return rows(mapper.selectScopedList(query(q).last("LIMIT " + limit)));
    }

    LambdaQueryWrapper<SalOrderDO> query(OrderQuery q) {
        LambdaQueryWrapper<SalOrderDO> w = new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getDeleted, false)
                .eq(q.getCustomerId() != null, SalOrderDO::getCustomerId, q.getCustomerId())
                .eq(q.getOwnerId() != null, SalOrderDO::getOwnerId, q.getOwnerId())
                .eq(StringUtils.hasText(q.getOrderType()), SalOrderDO::getOrderType, q.getOrderType())
                .ge(q.getDateFrom() != null, SalOrderDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, SalOrderDO::getDocDate, q.getDateTo())
                .eq(Boolean.TRUE.equals(q.getDeliveryRisk()), SalOrderDO::getDeliveryRisk, true);
        if (StringUtils.hasText(q.getDocNo())) {
            String no = q.getDocNo().trim();
            w.and(x -> x.likeRight(SalOrderDO::getDocNo, no.toUpperCase()).or().likeRight(SalOrderDO::getCustomerPoNo, no));
        }
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(SalOrderDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        StringBuilder cond = new StringBuilder();
        if (q.getMaterialId() != null) cond.append(" AND material_id = ").append(q.getMaterialId().longValue());
        if (q.getRequiredFrom() != null) cond.append(" AND required_date >= '").append(q.getRequiredFrom()).append("'");
        if (q.getRequiredTo() != null) cond.append(" AND required_date <= '").append(q.getRequiredTo()).append("'");
        if (Boolean.TRUE.equals(q.getUnshipped())) {
            cond.append(" AND line_status = 'OPEN' AND base_qty > shipped_qty");
            w.in(SalOrderDO::getStatus, ACTIVE);
        }
        if (!cond.isEmpty()) w.inSql(SalOrderDO::getId, "SELECT order_id FROM sal_order_line WHERE deleted = 0" + cond);
        return w.orderByDesc(SalOrderDO::getDocDate).orderByDesc(SalOrderDO::getId);
    }

    private List<OrderRow> rows(List<SalOrderDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, List<SalOrderLineDO>> lines = lineMapper.selectByParents(list.stream().map(SalOrderDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(SalOrderLineDO::getOrderId));
        Map<Long, CustomerDTO> cs = support.customers(list.stream().map(SalOrderDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(SalOrderDO::getOwnerId).toList());
        boolean cost = SalSupport.canViewOrderCost();
        BigDecimal minMargin = costService.minMarginRate();
        return list.stream().map(o -> {
            List<SalOrderLineDO> ls = lines.getOrDefault(o.getId(), List.of());
            return new OrderRow(o.getId(), o.getDocNo(), o.getOrderVersion(), o.getOrderType(), o.getCustomerId(), SalSupport.shortName(cs, o.getCustomerId()),
                    o.getCustomerPoNo(), o.getCurrency(), o.getTotalAmount(), SalSupport.mask(o.getMinMarginRate(), cost),
                    cost && o.getMinMarginRate() != null && o.getMinMarginRate().compareTo(minMargin) < 0,
                    ls.stream().map(SalOrderLineDO::getRequiredDate).min(Comparator.naturalOrder()).orElse(null),
                    ratio(o.getShippedAmount(), o.getTotalAmount()), ratio(o.getReceivedAmount(), o.getTotalAmount()),
                    Boolean.TRUE.equals(o.getDeliveryRisk()), o.getStatus().name(), o.getOwnerId(), SalSupport.name(users, o.getOwnerId()),
                    o.getDocDate(), o.getCreatedAt());
        }).toList();
    }

    static BigDecimal ratio(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) return BigDecimal.ZERO;
        return SalSupport.nz(part).divide(total, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
    }

    /** 订单明细（导出、报表），受数据权限约束 */
    public List<OrderLineRow> lineRows(OrderQuery q, int limit) {
        List<SalOrderDO> orders = mapper.selectScopedList(query(q).last("LIMIT " + Math.min(limit, 20000)));
        if (orders.isEmpty()) return List.of();
        Map<Long, SalOrderDO> byId = orders.stream().collect(Collectors.toMap(SalOrderDO::getId, o -> o));
        List<SalOrderLineDO> lines = lineMapper.selectByParents(byId.keySet());
        if (q.getMaterialId() != null) lines = lines.stream().filter(l -> l.getMaterialId().equals(q.getMaterialId())).toList();
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalOrderLineDO::getMaterialId).toList());
        Map<Long, CustomerDTO> cs = support.customers(orders.stream().map(SalOrderDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(orders.stream().map(SalOrderDO::getOwnerId).toList());
        boolean cost = SalSupport.canViewOrderCost();
        List<OrderLineRow> rows = new ArrayList<>();
        for (SalOrderDO o : orders) {
            for (SalOrderLineDO l : lines) {
                if (!l.getOrderId().equals(o.getId())) continue;
                if (rows.size() >= limit) return rows;
                MaterialDTO m = ms.get(l.getMaterialId());
                rows.add(new OrderLineRow(o.getId(), o.getDocNo(), o.getDocDate(), o.getStatus().name(), o.getCustomerId(),
                        SalSupport.shortName(cs, o.getCustomerId()), o.getCustomerPoNo(), SalSupport.name(users, o.getOwnerId()), o.getCurrency(),
                        o.getExchangeRate(), l.getLineNo(), l.getId(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                        m == null ? null : m.spec(), l.getCustomerPartNo(), l.getUom(), l.getQty(), l.getBaseQty(),
                        Boolean.TRUE.equals(o.getTaxIncluded()) ? l.getPriceInclTax() : l.getPrice(), l.getTotalAmount(),
                        support.currencyApi().toBase(l.getTotalAmount(), o.getExchangeRate()), l.getRequiredDate(), l.getPromisedDate(),
                        l.getShippedQty(), openQty(l), l.getInvoicedQty(), l.getLineStatus(), SalSupport.mask(l.getMarginRate(), cost)));
            }
        }
        return rows;
    }

    public static BigDecimal openQty(SalOrderLineDO l) {
        return CLOSED.equals(l.getLineStatus()) ? BigDecimal.ZERO : l.getBaseQty().subtract(l.getShippedQty()).max(BigDecimal.ZERO);
    }

    public static LocalDate dueDate(SalOrderLineDO l) {
        return l.getPromisedDate() != null ? l.getPromisedDate() : l.getRequiredDate();
    }

    static boolean delayed(SalOrderLineDO l) {
        return l.getPromisedDate() != null && l.getPromisedDate().isAfter(l.getRequiredDate()) && !CLOSED.equals(l.getLineStatus());
    }

    public OrderDetail detail(Long id) {
        SalOrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        CustomerDTO c = support.customer(o.getCustomerId());
        List<SalOrderLineDO> lines = lineMapper.selectByParent(id);
        boolean cost = SalSupport.canViewOrderCost();
        ContactDTO contact = o.getContactId() == null ? null
                : support.customerApi().getContacts(c.id()).stream().filter(x -> x.id().equals(o.getContactId())).findFirst().orElse(null);
        PaymentTermDTO term = o.getPaymentTermId() == null ? null : paymentTermApi.get(o.getPaymentTermId()).orElse(null);
        SalOrderChangeDO running = runningChange(id);
        SalQuotationDO qt = o.getQuotationId() == null ? null : quotationMapper.selectById(o.getQuotationId());
        Map<Long, UserDTO> users = support.users(List.of(SalSupport.nz(o.getOwnerId()), SalSupport.nz(o.getCreatedBy())));
        AddressDTO billTo = o.getBillToAddressId() == null ? null
                : support.customerApi().getAddresses(c.id(), null).stream().filter(a -> a.id().equals(o.getBillToAddressId())).findFirst().orElse(null);
        int risk = (int) lines.stream().filter(OrderService::delayed).count();
        return new OrderDetail(o.getId(), o.getDocNo(), o.getDocDate(), o.getStatus().name(), o.getOrderType(), c.id(), c.code(), c.shortName(), c.level(),
                o.getContactId(), contact == null ? null : contact.name(), o.getCustomerPoNo(), o.getCustomerPoDate(), o.getQuotationId(),
                qt == null ? null : qt.getDocNo() + " R" + qt.getRevision(), o.getCurrency(), o.getExchangeRate(), Boolean.TRUE.equals(o.getTaxIncluded()),
                o.getPaymentTermId(), term == null ? null : term.name(), o.getTradeTerm(), o.getPortOfLoading(), o.getPortOfDestination(),
                o.getShipToAddressId(), shipToText(o), o.getBillToAddressId(), billTo == null ? null : addressText(billTo), o.getAmount(), o.getTaxAmount(),
                o.getTotalAmount(), o.getTotalAmountBase(), o.getShippedAmount(), o.getReceivedAmount(), SalSupport.mask(o.getMinMarginRate(), cost),
                cost && Boolean.TRUE.equals(o.getBelowFloor()), Boolean.TRUE.equals(o.getCreditWarning()), o.getOrderVersion(),
                Boolean.TRUE.equals(o.getDeliveryRisk()), risk, o.getCloseReason(), o.getTerms(), o.getRemark(), o.getOwnerId(),
                SalSupport.name(users, o.getOwnerId()), o.getApprovedAt(), cost, running == null ? null : running.getId(),
                running == null ? null : running.getDocNo(), SalSupport.name(users, o.getCreatedBy()), o.getCreatedAt(), o.getVersion(),
                lineResps(lines, cost), related(o));
    }

    List<OrderLineResp> lineResps(List<SalOrderLineDO> lines, boolean cost) {
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalOrderLineDO::getMaterialId).toList());
        return lines.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            return new OrderLineResp(l.getId(), l.getLineNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.spec(), m == null ? null : m.baseUom(), l.getCustomerPartNo(), l.getDescription(), l.getUom(), l.getQty(),
                    l.getBaseQty(), l.getPrice(), l.getPriceInclTax(), l.getTaxRate(), l.getAmount(), l.getTaxAmount(), l.getTotalAmount(),
                    l.getPriceSource(), SalSupport.mask(l.getCostPrice(), cost), SalSupport.mask(l.getMarginRate(), cost),
                    cost && Boolean.TRUE.equals(l.getBelowFloor()), l.getRequiredDate(), l.getPromisedDate(), l.getPromiseRemark(), delayed(l),
                    l.getNoticedQty(), l.getShippedQty(), l.getReturnedQty(), l.getInvoicedQty(), openQty(l), l.getLineStatus(), l.getQuotationLineId(),
                    l.getRemark());
        }).toList();
    }

    private List<RelatedDoc> related(SalOrderDO o) {
        List<RelatedDoc> list = new ArrayList<>();
        if (o.getQuotationId() != null) {
            SalQuotationDO q = quotationMapper.selectById(o.getQuotationId());
            if (q != null) list.add(new RelatedDoc("UP", "报价单", q.getDocNo() + " R" + q.getRevision(), q.getDocDate(), q.getStatus().name(),
                    q.getStatus().label(), "/sales/quotation/" + q.getId()));
        }
        changeMapper.selectList(new LambdaQueryWrapper<SalOrderChangeDO>().eq(SalOrderChangeDO::getOrderId, o.getId()).orderByAsc(SalOrderChangeDO::getId))
                .forEach(c -> list.add(new RelatedDoc("DOWN", "订单变更单", c.getDocNo(), c.getDocDate(), c.getStatus().name(), c.getStatus().label(),
                        "/sales/order-change/" + c.getId())));
        Set<Long> returnIds = returnLineMapper.selectList(new LambdaQueryWrapper<SalReturnLineDO>().eq(SalReturnLineDO::getOrderId, o.getId())).stream()
                .map(SalReturnLineDO::getReturnId).collect(Collectors.toSet());
        if (!returnIds.isEmpty()) {
            returnMapper.selectBatchIds(returnIds).stream().sorted(Comparator.comparing(SalReturnDO::getId)).forEach(r -> list.add(new RelatedDoc("DOWN",
                    "销售退货单", r.getDocNo(), r.getDocDate(), r.getStatus().name(), r.getStatus().label(), "/sales/return/" + r.getId())));
        }
        Map<String, SalOrderExecDO> docs = new LinkedHashMap<>();
        for (SalOrderExecDO e : execMapper.selectList(new LambdaQueryWrapper<SalOrderExecDO>().eq(SalOrderExecDO::getOrderId, o.getId())
                .isNotNull(SalOrderExecDO::getDocNo).orderByAsc(SalOrderExecDO::getId))) {
            docs.putIfAbsent(e.getExecType() + "|" + e.getDocNo(), e);
        }
        docs.values().forEach(e -> list.add(new RelatedDoc("DOWN", execTypeName(e.getExecType()), e.getDocNo(), e.getExecDate(), e.getExecType(),
                execTypeName(e.getExecType()), null)));
        return list;
    }

    static String execTypeName(String t) {
        return switch (t) {
            case OrderExecService.NOTICE -> "出货通知";
            case OrderExecService.SHIP -> "出货单";
            case OrderExecService.SHIP_REVERSE -> "出货冲销";
            case OrderExecService.BL -> "提单";
            case OrderExecService.INVOICE -> "发票";
            case OrderExecService.RECEIPT -> "收款";
            default -> "退货";
        };
    }

    public List<ExecRow> execution(Long id) {
        SalOrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        Map<Long, Integer> lineNos = lineMapper.selectByParent(id).stream().collect(Collectors.toMap(SalOrderLineDO::getId, SalOrderLineDO::getLineNo));
        return execMapper.selectList(new LambdaQueryWrapper<SalOrderExecDO>().eq(SalOrderExecDO::getOrderId, id).orderByAsc(SalOrderExecDO::getId)).stream()
                .map(e -> new ExecRow(e.getId(), e.getOrderLineId(), e.getOrderLineId() == null ? null : lineNos.get(e.getOrderLineId()), e.getExecType(),
                        e.getDocType(), e.getDocNo(), e.getQty(), e.getAmount(), e.getExecDate(), e.getCreatedAt())).toList();
    }

    public List<SnapshotRow> snapshots(Long id) {
        SalOrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        List<SalOrderSnapshotDO> list = snapshotMapper.selectList(new LambdaQueryWrapper<SalOrderSnapshotDO>().eq(SalOrderSnapshotDO::getOrderId, id)
                .orderByAsc(SalOrderSnapshotDO::getOrderVersion));
        Map<Long, SalOrderChangeDO> changes = list.stream().map(SalOrderSnapshotDO::getChangeId).filter(Objects::nonNull).distinct()
                .map(changeMapper::selectById).filter(Objects::nonNull).collect(Collectors.toMap(SalOrderChangeDO::getId, c -> c));
        return list.stream().map(s -> new SnapshotRow(s.getId(), s.getOrderVersion(), s.getChangeId(),
                s.getChangeId() == null || !changes.containsKey(s.getChangeId()) ? null : changes.get(s.getChangeId()).getDocNo(), s.getContent(),
                s.getCreatedAt())).toList();
    }

    String shipToText(SalOrderDO o) {
        AddressDTO a = o.getShipToSnapshot() == null ? null : support.fromJson(o.getShipToSnapshot(), AddressDTO.class);
        return a == null ? null : addressText(a);
    }

    static String addressText(AddressDTO a) {
        StringBuilder sb = new StringBuilder();
        sb.append(a.companyName());
        if (StringUtils.hasText(a.contactName())) sb.append(" / ").append(a.contactName());
        if (StringUtils.hasText(a.phone())) sb.append(" ").append(a.phone());
        sb.append("，").append(a.addressLine());
        for (String s : new String[]{a.city(), a.province(), a.zip(), a.country()}) {
            if (StringUtils.hasText(s)) sb.append(", ").append(s);
        }
        return sb.toString();
    }

    /** 选择客户后的默认值（R01 在提交时校验，这里允许所有客户以便显示提示） */
    public CustomerDefaults customerDefaults(Long customerId) {
        CustomerDTO c = support.customer(customerId);
        String base = support.baseCurrency();
        String currency = StringUtils.hasText(c.currency()) ? c.currency() : base;
        BigDecimal rate = null;
        try {
            rate = currency.equals(base) ? BigDecimal.ONE : support.currencyApi().getRate(currency, LocalDate.now());
        } catch (BizException ignored) {
            // 未维护汇率时由用户手工录入
        }
        List<AddressDTO> addrs = support.customerApi().getAddresses(c.id(), null);
        List<ContactDTO> contacts = support.customerApi().getContacts(c.id());
        Long shipTo = addrs.stream().filter(a -> "SHIP_TO".equals(a.type()) && a.isDefault()).map(AddressDTO::id).findFirst()
                .orElse(addrs.stream().filter(a -> "SHIP_TO".equals(a.type())).map(AddressDTO::id).findFirst().orElse(null));
        Long billTo = addrs.stream().filter(a -> "BILL_TO".equals(a.type()) && a.isDefault()).map(AddressDTO::id).findFirst().orElse(null);
        Long contact = contacts.stream().filter(ContactDTO::primary).map(ContactDTO::id).findFirst().orElse(null);
        return new CustomerDefaults(c.id(), c.shortName(), c.status().name(), c.foreign(), currency, rate, !c.foreign(), taxRate(c), c.paymentTermId(),
                c.tradeTerm(), c.ownerId(), support.userName(c.ownerId()), shipTo, billTo, contact,
                addrs.stream().map(a -> new AddressOption(a.id(), a.type(), addressText(a), a.isDefault())).toList(),
                contacts.stream().map(x -> new ContactOption(x.id(), x.name(), x.title(), x.email(), x.mobile() != null ? x.mobile() : x.phone(),
                        x.primary())).toList());
    }

    /** 客户销项税率，没有时取默认销项税率 */
    BigDecimal taxRate(CustomerDTO c) {
        return c.salesTaxRate() != null ? c.salesTaxRate() : priceListService.defaultTaxRate();
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(OrderSave req) {
        SalOrderDO o = new SalOrderDO();
        o.setDocNo(docNo(req.docNo(), null));
        o.setStatus(DocStatus.DRAFT);
        o.setOrderVersion(1);
        initAmounts(o);
        CustomerDTO c = fillHeader(o, req);
        mapper.insert(o);
        List<String> warnings = saveLines(o, c, req.lines());
        mapper.updateByIdOrFail(o);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, o.getId());
        return new SaveResult(o.getId(), warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult update(Long id, OrderSave req) {
        SalOrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        SalSupport.requireDraft(o);
        if (req.version() != null) o.setVersion(req.version());
        if (StringUtils.hasText(req.docNo()) && !req.docNo().trim().equals(o.getDocNo())) o.setDocNo(docNo(req.docNo(), id));
        CustomerDTO c = fillHeader(o, req);
        List<String> warnings = saveLines(o, c, req.lines());
        mapper.updateByIdOrFail(o);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, o.getId());
        return new SaveResult(o.getId(), warnings);
    }

    /** 单号：编码规则允许手工时可录入（唯一） */
    private String docNo(String manual, Long selfId) {
        if (!StringUtils.hasText(manual)) return selfId == null ? support.nextNo(BIZ_TYPE) : null;
        if (!support.manualCodeAllowed(BIZ_TYPE)) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "销售订单号不允许手工录入");
        String no = manual.trim();
        SalOrderDO dup = mapper.selectOne(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getDocNo, no).last("LIMIT 1"));
        if (dup != null && !dup.getId().equals(selfId)) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "单号「" + no + "」已存在");
        return no;
    }

    static void initAmounts(SalOrderDO o) {
        o.setAmount(BigDecimal.ZERO);
        o.setTaxAmount(BigDecimal.ZERO);
        o.setTotalAmount(BigDecimal.ZERO);
        o.setTotalAmountBase(BigDecimal.ZERO);
        o.setShippedAmount(BigDecimal.ZERO);
        o.setReceivedAmount(BigDecimal.ZERO);
        o.setBelowFloor(false);
        o.setCreditWarning(false);
        o.setDeliveryRisk(false);
    }

    private CustomerDTO fillHeader(SalOrderDO o, OrderSave req) {
        String type = StringUtils.hasText(req.orderType()) ? req.orderType() : "NORMAL";
        if (!TYPES.contains(type)) throw new BizException(SalesErrorCodes.ORDER_TYPE_INVALID);
        CustomerDTO c = support.customer(req.customerId());
        o.setOrderType(type);
        o.setCustomerId(c.id());
        o.setDocDate(req.docDate() != null ? req.docDate() : o.getDocDate() != null ? o.getDocDate() : LocalDate.now());
        List<ContactDTO> contacts = support.customerApi().getContacts(c.id());
        if (req.contactId() != null && contacts.stream().noneMatch(x -> x.id().equals(req.contactId()))) {
            throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "联系人");
        }
        Long contactId = req.contactId() != null ? req.contactId() : contacts.stream().filter(ContactDTO::primary).map(ContactDTO::id).findFirst().orElse(null);
        o.setContactId(contactId);
        String po = SalSupport.trim(req.customerPoNo());
        if (po != null) {
            // R02：同一客户 + 客户 PO 号唯一（未作废订单）
            SalOrderDO dup = mapper.selectOne(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getCustomerId, c.id()).eq(SalOrderDO::getCustomerPoNo, po)
                    .ne(SalOrderDO::getStatus, DocStatus.VOIDED).ne(o.getId() != null, SalOrderDO::getId, o.getId()).last("LIMIT 1"));
            if (dup != null) throw BizException.of(SalesErrorCodes.ORDER_PO_DUPLICATE, po, dup.getDocNo());
        }
        o.setCustomerPoNo(po);
        o.setCustomerPoDate(req.customerPoDate());
        String currency = StringUtils.hasText(req.currency()) ? req.currency().trim().toUpperCase()
                : StringUtils.hasText(c.currency()) ? c.currency() : support.baseCurrency();
        support.currencyApi().validate(currency);
        o.setCurrency(currency);
        BigDecimal rate = currency.equals(support.baseCurrency()) ? BigDecimal.ONE
                : req.exchangeRate() != null ? req.exchangeRate() : support.currencyApi().getRate(currency, o.getDocDate());
        if (rate.signum() <= 0) throw new BizException(SalesErrorCodes.EXCHANGE_RATE_POSITIVE);
        o.setExchangeRate(rate);
        o.setTaxIncluded(req.taxIncluded() != null ? req.taxIncluded() : !c.foreign());
        Long termId = req.paymentTermId() != null ? req.paymentTermId() : c.paymentTermId();
        if (termId == null) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "请选择付款条件");
        PaymentTermDTO term = paymentTermApi.validate(termId, "SALES");
        o.setPaymentTermId(termId);
        o.setPaymentTermSnapshot(support.json(term));
        String trade = req.tradeTerm() != null ? SalSupport.trim(req.tradeTerm()) : c.tradeTerm();
        if (trade != null && !trade.equals(o.getTradeTerm())) support.dict().validate("sys_trade_term", trade, "贸易条款");
        o.setTradeTerm(trade);
        o.setPortOfLoading(SalSupport.trim(req.portOfLoading()));
        o.setPortOfDestination(SalSupport.trim(req.portOfDestination()));
        List<AddressDTO> addrs = support.customerApi().getAddresses(c.id(), null);
        Long shipTo = req.shipToAddressId() != null ? req.shipToAddressId()
                : addrs.stream().filter(a -> "SHIP_TO".equals(a.type()) && a.isDefault()).map(AddressDTO::id).findFirst()
                .orElse(addrs.stream().filter(a -> "SHIP_TO".equals(a.type())).map(AddressDTO::id).findFirst().orElse(null));
        AddressDTO ship = shipTo == null ? null : addrs.stream().filter(a -> a.id().equals(shipTo)).findFirst()
                .orElseThrow(() -> BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "收货地址"));
        o.setShipToAddressId(shipTo);
        o.setShipToSnapshot(ship == null ? null : support.json(ship));
        Long billTo = req.billToAddressId() != null ? req.billToAddressId()
                : addrs.stream().filter(a -> "BILL_TO".equals(a.type()) && a.isDefault()).map(AddressDTO::id).findFirst().orElse(null);
        if (billTo != null && addrs.stream().noneMatch(a -> a.id().equals(billTo))) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "开票地址");
        o.setBillToAddressId(billTo);
        o.setTerms(SalSupport.trim(req.terms()));
        o.setRemark(SalSupport.trim(req.remark()));
        support.fillOwner(o, req.ownerId() != null ? req.ownerId() : o.getOwnerId() != null ? o.getOwnerId() : c.ownerId());
        return c;
    }

    /** 整体替换明细：客户料号对照、取价、换算、金额、成本毛利（R04、R05、R07） */
    List<String> saveLines(SalOrderDO o, CustomerDTO c, List<OrderLineSave> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(SalesErrorCodes.DOC_NO_LINES);
        List<OrderLineSave> resolved = new ArrayList<>();
        for (OrderLineSave l : lines) {
            if (l.materialId() == null && StringUtils.hasText(l.customerPartNo())) {
                CustomerPartDTO cp = customerPartApi.toMaterial(c.id(), l.customerPartNo().trim())
                        .orElseThrow(() -> BizException.of(SalesErrorCodes.CUSTOMER_PART_NOT_MAPPED, l.customerPartNo().trim()));
                resolved.add(new OrderLineSave(cp.materialId(), cp.customerPartNo(), l.description(), l.uom(), l.qty(), l.price(), l.taxRate(),
                        l.requiredDate(), l.quotationLineId(), l.priceSource(), l.remark()));
            } else {
                resolved.add(l);
            }
        }
        Map<Long, MaterialDTO> ms = support.materials(resolved.stream().map(OrderLineSave::materialId).toList());
        Map<Long, CostService.UnitCost> costs = costService.unitCosts(ms.keySet());
        BigDecimal minMargin = costService.minMarginRate();
        String moqCheck = support.params().getString(SalesModuleConfig.P_MOQ_CHECK);
        boolean replacement = REPLACEMENT.equals(o.getOrderType());
        lineMapper.deleteByParent(o.getId());
        List<String> warnings = new ArrayList<>();
        int no = 0;
        for (OrderLineSave l : resolved) {
            no++;
            MaterialDTO m = l.materialId() == null ? null : ms.get(l.materialId());
            if (m == null) throw BizException.of(SalesErrorCodes.LINE_FIELD_REQUIRED, no, "物料");
            if (l.qty() == null || l.qty().signum() <= 0) throw BizException.of(SalesErrorCodes.LINE_QTY_POSITIVE, no);
            if (l.requiredDate() == null) throw BizException.of(SalesErrorCodes.LINE_FIELD_REQUIRED, no, "要求交期");
            SalOrderLineDO d = new SalOrderLineDO();
            d.setOrderId(o.getId());
            d.setLineNo(no);
            d.setMaterialId(m.id());
            d.setUom(StringUtils.hasText(l.uom()) ? l.uom().trim() : m.baseUom());
            d.setQty(Decimals.qty(l.qty()));
            d.setBaseQty(Decimals.qty(support.toBase(m.id(), d.getQty(), d.getUom())));
            CustomerPartDTO cp = StringUtils.hasText(l.customerPartNo())
                    ? customerPartApi.toMaterial(c.id(), l.customerPartNo().trim()).filter(x -> x.materialId().equals(m.id())).orElse(null)
                    : customerPartApi.toCustomerPart(c.id(), m.id()).orElse(null);
            d.setCustomerPartId(cp == null ? null : cp.id());
            d.setCustomerPartNo(cp != null ? cp.customerPartNo() : SalSupport.trim(l.customerPartNo()));
            d.setDescription(StringUtils.hasText(l.description()) ? l.description().trim() : defaultDescription(m, c.foreign()));
            d.setTaxRate(l.taxRate() != null ? l.taxRate() : taxRate(c));
            d.setRequiredDate(l.requiredDate());
            d.setQuotationLineId(l.quotationLineId());
            d.setRemark(SalSupport.trim(l.remark()));
            initQty(d);
            BigDecimal price = l.price();
            String source = SalSupport.trim(l.priceSource());
            if (replacement) {
                price = BigDecimal.ZERO;
                source = "补货免费";
            } else if (price == null) {
                Optional<SalesPriceDTO> found = priceLookupService.getPrice(c.id(), m.id(), d.getQty(), d.getUom(), o.getDocDate(), o.getCurrency());
                if (found.isPresent()) {
                    price = PriceLookupService.convert(found.get(), Boolean.TRUE.equals(o.getTaxIncluded()), d.getTaxRate());
                    source = found.get().sourceLabel();
                } else {
                    price = BigDecimal.ZERO;
                    warnings.add("第 " + no + " 行物料「" + m.code() + "」没有可用价格，请录入单价");
                }
            } else if (source == null) {
                source = "手工";
            }
            if (price.signum() < 0) throw BizException.of(SalesErrorCodes.LINE_PRICE_NEGATIVE, no);
            d.setPriceSource(source);
            computeAmounts(d, Boolean.TRUE.equals(o.getTaxIncluded()), price);
            CostService.UnitCost uc = costs.get(m.id());
            if (!replacement) {
                CostService.Margin mg = costService.margin(d.getPrice(), o.getExchangeRate(), basePerUom(d), uc == null ? null : uc.cost(), minMargin);
                d.setCostPrice(mg.cost());
                d.setMarginRate(mg.marginRate());
                d.setBelowFloor(mg.belowFloor());
            } else {
                d.setBelowFloor(false);
            }
            moqWarnings(m, d, no, moqCheck, warnings);
            lineMapper.insert(d);
        }
        recalcTotals(o);
        return warnings;
    }

    static String defaultDescription(MaterialDTO m, boolean foreign) {
        String name = foreign && StringUtils.hasText(m.nameEn()) ? m.nameEn() : m.name();
        return StringUtils.hasText(m.spec()) ? name + " " + m.spec() : name;
    }

    static void initQty(SalOrderLineDO d) {
        d.setNoticedQty(BigDecimal.ZERO);
        d.setShippedQty(BigDecimal.ZERO);
        d.setReturnedQty(BigDecimal.ZERO);
        d.setInvoicedQty(BigDecimal.ZERO);
        d.setLineStatus(OPEN);
    }

    static BigDecimal basePerUom(SalOrderLineDO d) {
        return d.getQty().signum() == 0 ? BigDecimal.ONE : d.getBaseQty().divide(d.getQty(), 10, RoundingMode.HALF_UP);
    }

    /** R07：含税录入时 price = ROUND(含税价 ÷ (1 + 税率), 6)，价税合计 = ROUND(数量 × 含税价, 2)，不含税金额 = ROUND(价税合计 ÷ (1 + 税率), 2)；不含税录入反向计算 */
    public static void computeAmounts(SalOrderLineDO d, boolean taxIncluded, BigDecimal inputPrice) {
        BigDecimal rate = d.getTaxRate();
        BigDecimal onePlus = BigDecimal.ONE.add(rate);
        if (taxIncluded) {
            d.setPriceInclTax(Decimals.price(inputPrice));
            d.setPrice(Decimals.price(inputPrice.divide(onePlus, 10, RoundingMode.HALF_UP)));
            d.setTotalAmount(Decimals.multiplyAmount(d.getQty(), d.getPriceInclTax()));
            d.setAmount(Decimals.amount(d.getTotalAmount().divide(onePlus, 10, RoundingMode.HALF_UP)));
            d.setTaxAmount(d.getTotalAmount().subtract(d.getAmount()));
        } else {
            d.setPrice(Decimals.price(inputPrice));
            d.setPriceInclTax(Decimals.price(inputPrice.multiply(onePlus)));
            d.setAmount(Decimals.multiplyAmount(d.getQty(), d.getPrice()));
            d.setTaxAmount(Decimals.amount(d.getAmount().multiply(rate)));
            d.setTotalAmount(d.getAmount().add(d.getTaxAmount()));
        }
    }

    /** R04：数量低于物料 MOQ 按参数警告或阻止；非 MPQ 倍数提示 */
    private void moqWarnings(MaterialDTO m, SalOrderLineDO d, int no, String mode, List<String> warnings) {
        if ("NONE".equals(mode)) return;
        MaterialPlanAttr a;
        try {
            a = support.materialApi().getPlanAttr(m.id());
        } catch (RuntimeException e) {
            return;
        }
        if (a == null) return;
        if (a.moq() != null && a.moq().signum() > 0 && d.getBaseQty().compareTo(a.moq()) < 0) {
            if ("BLOCK".equals(mode)) throw BizException.of(SalesErrorCodes.ORDER_BELOW_MOQ, m.code(), SalSupport.plain(a.moq()) + " " + m.baseUom());
            warnings.add("第 " + no + " 行物料「" + m.code() + "」数量低于最小订购量 " + SalSupport.plain(a.moq()) + " " + m.baseUom());
        }
        if (a.mpq() != null && a.mpq().signum() > 0 && d.getBaseQty().remainder(a.mpq()).signum() != 0) {
            warnings.add("第 " + no + " 行物料「" + m.code() + "」数量不是最小包装量 " + SalSupport.plain(a.mpq()) + " 的整数倍");
        }
    }

    /** 单头合计、本位币、最低毛利率、是否低于底价 */
    void recalcTotals(SalOrderDO o) {
        List<SalOrderLineDO> lines = lineMapper.selectByParent(o.getId());
        List<SalOrderLineDO> all = lines;
        o.setAmount(SalSupport.sum(lines.stream().map(SalOrderLineDO::getAmount).toList()));
        o.setTaxAmount(SalSupport.sum(lines.stream().map(SalOrderLineDO::getTaxAmount).toList()));
        o.setTotalAmount(SalSupport.sum(lines.stream().map(SalOrderLineDO::getTotalAmount).toList()));
        o.setTotalAmountBase(support.currencyApi().toBase(o.getTotalAmount(), o.getExchangeRate()));
        o.setMinMarginRate(all.stream().map(SalOrderLineDO::getMarginRate).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null));
        o.setBelowFloor(all.stream().anyMatch(l -> Boolean.TRUE.equals(l.getBelowFloor())));
        o.setDeliveryRisk(all.stream().anyMatch(OrderService::delayed));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SalOrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        SalSupport.requireDraft(o);
        if (o.getOrderVersion() > 1 || o.getApprovedAt() != null) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "订单曾经审核过，不能删除，请作废");
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        SalOrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        support.fire(SalStateMachines.ORDER, mapper, o, BIZ_TYPE, SalAction.VOID, SalSupport.requireReason(reason, "作废"));
    }

    /** 复制为新草稿（单据日期今天，要求交期早于今天的改为今天） */
    @Transactional(rollbackFor = Exception.class)
    public SaveResult copy(Long id) {
        SalOrderDO src = getOrThrow(id);
        DataScopes.check(src.getOrgId(), src.getDeptId(), src.getOwnerId(), "销售订单");
        LocalDate today = LocalDate.now();
        boolean incl = Boolean.TRUE.equals(src.getTaxIncluded());
        List<OrderLineSave> lines = lineMapper.selectByParent(id).stream().map(l -> new OrderLineSave(l.getMaterialId(), l.getCustomerPartNo(),
                l.getDescription(), l.getUom(), l.getQty(), incl ? l.getPriceInclTax() : l.getPrice(), l.getTaxRate(),
                l.getRequiredDate().isBefore(today) ? today : l.getRequiredDate(), null, l.getPriceSource(), l.getRemark())).toList();
        return create(new OrderSave(null, src.getOrderType(), src.getCustomerId(), src.getContactId(), null, null, today, src.getOwnerId(),
                src.getCurrency(), null, src.getTaxIncluded(), src.getPaymentTermId(), src.getTradeTerm(), src.getPortOfLoading(),
                src.getPortOfDestination(), src.getShipToAddressId(), src.getBillToAddressId(), src.getTerms(), src.getRemark(), lines, null, null));
    }

    // ==================== 提交 / 审核 ====================

    /**
     * 提交（R01～R07）：信用检查为 WARN 时需要用户确认（confirmCredit），BLOCK 时阻止；补货订单不检查信用。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id, boolean confirmCredit) {
        SalOrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        SalSupport.requireDraft(o);
        CustomerDTO c = support.customerApi().validateCanOrder(o.getCustomerId());
        List<SalOrderLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(SalesErrorCodes.DOC_NO_LINES);
        if (o.getExchangeRate() == null || o.getExchangeRate().signum() <= 0) throw new BizException(SalesErrorCodes.EXCHANGE_RATE_POSITIVE);
        if (o.getShipToAddressId() == null) throw new BizException(SalesErrorCodes.ORDER_SHIP_ADDRESS_REQUIRED);
        List<String> warnings = new ArrayList<>();
        for (SalOrderLineDO l : lines) {
            support.materialApi().validateUsable(l.getMaterialId());
            if (l.getRequiredDate().isBefore(o.getDocDate())) throw BizException.of(SalesErrorCodes.ORDER_REQUIRED_BEFORE_DOC, l.getLineNo());
            if (!REPLACEMENT.equals(o.getOrderType()) && l.getPriceInclTax().signum() == 0) {
                warnings.add("第 " + l.getLineNo() + " 行单价为 0");
            }
        }
        // R05：毛利与底价（成本可能已变化，提交时重新计算）
        refreshMargins(o, lines);
        if (!"IGNORE".equals(support.params().getString(SalesModuleConfig.P_NO_COST_POLICY)) && !REPLACEMENT.equals(o.getOrderType())) {
            Map<Long, MaterialDTO> ms = support.materials(lines.stream().filter(l -> l.getCostPrice() == null).map(SalOrderLineDO::getMaterialId).toList());
            ms.values().forEach(m -> warnings.add("物料「" + m.code() + "」没有成本，无法计算毛利"));
        }
        // R06：信用检查
        o.setCreditWarning(false);
        if (!REPLACEMENT.equals(o.getOrderType())) {
            CreditCheckResult cr = creditApi.check(c.id(), o.getTotalAmountBase(), CreditCheckPoint.ORDER);
            if (!cr.pass()) throw BizException.of(SalesErrorCodes.ORDER_CREDIT_BLOCKED, cr.message());
            if (cr.message() != null && "WARN".equals(cr.mode())) {
                if (!confirmCredit) {
                    throw BizException.of(SalesErrorCodes.ORDER_CREDIT_CONFIRM, cr.message()).withData(Map.of("needConfirm", true, "message", cr.message()));
                }
                o.setCreditWarning(true);
            }
        }
        mapper.updateByIdOrFail(o);
        support.fire(SalStateMachines.ORDER, mapper, o, BIZ_TYPE, SalAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountBase", o.getTotalAmountBase());
        vars.put("minMarginRate", o.getMinMarginRate() == null ? null : o.getMinMarginRate().multiply(SalSupport.HUNDRED));
        vars.put("belowFloor", Boolean.TRUE.equals(o.getBelowFloor()));
        vars.put("customerLevel", c.level());
        vars.put("orderType", o.getOrderType());
        vars.put("creditWarning", Boolean.TRUE.equals(o.getCreditWarning()));
        Map<String, Long> users = new HashMap<>();
        users.put("ownerId", o.getOwnerId());
        StartResult r = workflowApi.start(BIZ_TYPE, id, o.getDocNo(), "销售订单 " + o.getDocNo() + " " + c.shortName(), vars, users, support.currentUser());
        if (!r.isStarted()) approve(o);
        return new DocResult(o.getStatus().name(), warnings);
    }

    private void refreshMargins(SalOrderDO o, List<SalOrderLineDO> lines) {
        if (REPLACEMENT.equals(o.getOrderType())) return;
        Map<Long, CostService.UnitCost> costs = costService.unitCosts(lines.stream().map(SalOrderLineDO::getMaterialId).toList());
        BigDecimal minMargin = costService.minMarginRate();
        for (SalOrderLineDO l : lines) {
            CostService.UnitCost uc = costs.get(l.getMaterialId());
            CostService.Margin mg = costService.margin(l.getPrice(), o.getExchangeRate(), basePerUom(l), uc == null ? null : uc.cost(), minMargin);
            if (!Objects.equals(mg.cost(), l.getCostPrice()) || !Objects.equals(mg.marginRate(), l.getMarginRate())
                    || mg.belowFloor() != Boolean.TRUE.equals(l.getBelowFloor())) {
                l.setCostPrice(mg.cost());
                l.setMarginRate(mg.marginRate());
                l.setBelowFloor(mg.belowFloor());
                lineMapper.updateByIdOrFail(l);
            }
        }
        recalcTotals(o);
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        SalOrderDO o = getOrThrow(e.getBizId());
        if (o.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> {
                // 审核通过时再次信用检查：BLOCK 时审批失败
                if (!REPLACEMENT.equals(o.getOrderType())) {
                    CreditCheckResult cr = creditApi.check(o.getCustomerId(), o.getTotalAmountBase(), CreditCheckPoint.ORDER);
                    if (!cr.pass()) throw BizException.of(SalesErrorCodes.ORDER_CREDIT_BLOCKED, cr.message());
                }
                approve(o);
            }
            case WITHDRAWN -> support.fire(SalStateMachines.ORDER, mapper, o, BIZ_TYPE, SalAction.WITHDRAW, null);
            default -> support.fire(SalStateMachines.ORDER, mapper, o, BIZ_TYPE, SalAction.REJECT, e.getComment());
        }
    }

    /**
     * 审核：生成回款计划、冲销预测、回写客户最近下单日期、商机赢单；发布 SalesOrderApprovedEvent（PMC 需求池）与信用占用变化。
     */
    private void approve(SalOrderDO o) {
        o.setApprovedAt(LocalDateTime.now());
        support.fire(SalStateMachines.ORDER, mapper, o, BIZ_TYPE, SalAction.APPROVE, null);
        List<SalOrderLineDO> lines = lineMapper.selectByParent(o.getId());
        paymentPlanService.generate(o);
        Set<Long> consumed = new HashSet<>();
        for (SalOrderLineDO l : lines) forecastService.sync(o, l, l.getBaseQty(), consumed);
        forecastService.publishConsumed(consumed);
        support.customerApi().recordOrder(o.getCustomerId(), o.getDocDate());
        if (o.getQuotationId() != null) {
            SalQuotationDO q = quotationMapper.selectById(o.getQuotationId());
            if (q != null && q.getOpportunityId() != null) opportunityApi.onOrderApproved(q.getOpportunityId(), o.getDocNo());
        }
        eventPublisher.publish(new SalesOrderApprovedEvent(o.getId(), o.getDocNo(), o.getOrderType(), o.getCustomerId(), o.getOwnerId(), lineInfos(lines)));
        openAmountChanged(o);
    }

    void openAmountChanged(SalOrderDO o) {
        creditApi.refresh(List.of(o.getCustomerId()));
        eventPublisher.publish(new SalesOrderOpenAmountChangedEvent(o.getCustomerId(), o.getId()));
    }

    static List<SalesOrderLineInfo> lineInfos(List<SalOrderLineDO> lines) {
        return lines.stream().map(l -> new SalesOrderLineInfo(l.getId(), l.getLineNo(), l.getMaterialId(), l.getBaseQty(), l.getShippedQty(),
                l.getRequiredDate(), l.getPromisedDate(), l.getLineStatus())).toList();
    }

    /** R08：无出货通知、无收款、无生产订单等引用时才能反审核；删除回款计划、回退预测冲销 */
    @Transactional(rollbackFor = Exception.class)
    public void unapprove(Long id, String reason) {
        SalOrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        String why = SalSupport.requireReason(reason, "反审核");
        if (o.getStatus() != DocStatus.APPROVED) throw BizException.of(SalesErrorCodes.ORDER_STATUS, o.getStatus().label(), "反审核");
        List<SalOrderLineDO> lines = lineMapper.selectByParent(id);
        boolean noticed = lines.stream().anyMatch(l -> l.getNoticedQty().signum() > 0 || l.getShippedQty().signum() > 0);
        boolean referenced = referenceCheckers.stream().map(ch -> ch.findReference(id)).anyMatch(Optional::isPresent);
        if (noticed || SalSupport.nz(o.getReceivedAmount()).signum() != 0 || referenced) throw new BizException(SalesErrorCodes.ORDER_CANNOT_UNAPPROVE);
        checkNoRunningChange(id);
        o.setApprovedAt(null);
        support.fire(SalStateMachines.ORDER, mapper, o, BIZ_TYPE, SalAction.UNAPPROVE, why);
        paymentPlanService.deleteByOrder(id);
        Set<Long> consumed = new HashSet<>();
        for (SalOrderLineDO l : lines) forecastService.sync(o, l, BigDecimal.ZERO, consumed);
        forecastService.publishConsumed(consumed);
        eventPublisher.publish(new SalesOrderUnapprovedEvent(o.getId(), o.getDocNo()));
        openAmountChanged(o);
    }

    SalOrderChangeDO runningChange(Long orderId) {
        return changeMapper.selectOne(new LambdaQueryWrapper<SalOrderChangeDO>().eq(SalOrderChangeDO::getOrderId, orderId)
                .in(SalOrderChangeDO::getStatus, DocStatus.DRAFT, DocStatus.PENDING_APPROVAL).last("LIMIT 1"));
    }

    void checkNoRunningChange(Long orderId) {
        SalOrderChangeDO c = runningChange(orderId);
        if (c != null) throw BizException.of(SalesErrorCodes.ORDER_CHANGE_RUNNING, c.getDocNo());
    }

    /**
     * R10：原因必填；有未完成（已通知未出库）的出货通知时不能关闭；未出齐的行关闭，剩余数量从 PMC 需求移除，
     * 未出货部分的出货类回款计划取消，超出已出货的预测冲销回退。
     */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String reason) {
        SalOrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        String why = SalSupport.requireReason(reason, "关闭");
        List<SalOrderLineDO> lines = lineMapper.selectByParent(id);
        for (SalOrderLineDO l : lines) {
            if (l.getNoticedQty().compareTo(l.getShippedQty()) > 0) {
                SalOrderExecDO notice = execMapper.selectOne(new LambdaQueryWrapper<SalOrderExecDO>().eq(SalOrderExecDO::getOrderLineId, l.getId())
                        .eq(SalOrderExecDO::getExecType, OrderExecService.NOTICE).orderByDesc(SalOrderExecDO::getId).last("LIMIT 1"));
                throw BizException.of(SalesErrorCodes.ORDER_HAS_OPEN_NOTICE, notice == null ? "第 " + l.getLineNo() + " 行" : notice.getDocNo());
            }
        }
        checkNoRunningChange(id);
        Set<Long> consumed = new HashSet<>();
        for (SalOrderLineDO l : lines) {
            if (OPEN.equals(l.getLineStatus())) {
                l.setLineStatus(CLOSED);
                lineMapper.updateByIdOrFail(l);
            }
            forecastService.sync(o, l, l.getShippedQty(), consumed);
        }
        forecastService.publishConsumed(consumed);
        o.setCloseReason(why.length() > 256 ? why.substring(0, 256) : why);
        o.setDeliveryRisk(false);
        support.fire(SalStateMachines.ORDER, mapper, o, BIZ_TYPE, SalAction.CLOSE, why);
        paymentPlanService.onClosed(id);
        eventPublisher.publish(new SalesOrderClosedEvent(o.getId(), o.getDocNo(), why, lineInfos(lineMapper.selectByParent(id))));
        openAmountChanged(o);
    }

    // ==================== 打印 ====================

    /**
     * 打印数据：template = CONTRACT（销售合同，中文）/ PI（Proforma Invoice，英文）；lang 为空时合同中文、PI 英文。
     */
    public Map<String, Object> printData(Long id, String template, String lang) {
        OrderDetail d = detail(id);
        SalOrderDO o = getOrThrow(id);
        boolean en = lang != null ? lang.toLowerCase().startsWith("en") : "PI".equalsIgnoreCase(template);
        CustomerDTO c = support.customer(d.customerId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("docDate", d.docDate());
        data.put("status", d.status());
        data.put("orderVersion", d.orderVersion());
        OrgDTO company = support.orgs(List.of(SalSupport.nz(o.getOrgId()))).values().stream().findFirst().orElse(null);
        data.put("companyName", company == null ? "" : (en && company.nameEn() != null ? company.nameEn() : company.name()));
        data.put("companyAddress", company == null ? "" : Objects.toString(en && company.addressEn() != null ? company.addressEn() : company.address(), ""));
        data.put("customerName", en && StringUtils.hasText(c.nameEn()) ? c.nameEn() : c.name());
        data.put("customerPoNo", Objects.toString(d.customerPoNo(), ""));
        data.put("contactName", Objects.toString(d.contactName(), ""));
        data.put("shipTo", Objects.toString(d.shipToText(), ""));
        data.put("currency", d.currency());
        PaymentTermDTO term = support.fromJson(o.getPaymentTermSnapshot(), PaymentTermDTO.class);
        data.put("paymentTermName", term == null ? "" : en && StringUtils.hasText(term.nameEn()) ? term.nameEn() : term.name());
        data.put("tradeTerm", Objects.toString(d.tradeTerm(), ""));
        data.put("portOfLoading", Objects.toString(d.portOfLoading(), ""));
        data.put("portOfDestination", Objects.toString(d.portOfDestination(), ""));
        data.put("taxIncludedText", d.taxIncluded() ? (en ? "Tax included" : "含税") : (en ? "Tax excluded" : "不含税"));
        data.put("amount", d.amount());
        data.put("taxAmount", d.taxAmount());
        data.put("totalAmount", d.totalAmount());
        data.put("terms", Objects.toString(d.terms(), ""));
        data.put("remark", Objects.toString(d.remark(), ""));
        data.put("ownerName", Objects.toString(d.ownerName(), ""));
        String bank = support.params().getString(SalesModuleConfig.P_PI_BANK_INFO);
        data.put("bankInfo", StringUtils.hasText(bank) ? Arrays.stream(bank.split(";")).map(String::trim).filter(StringUtils::hasText).toList() : List.of());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (OrderLineResp l : d.lines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.lineNo());
            m.put("materialCode", l.materialCode());
            m.put("customerPartNo", Objects.toString(l.customerPartNo(), ""));
            m.put("description", Objects.toString(l.description(), l.materialName()));
            m.put("uom", l.uom());
            m.put("qty", l.qty());
            m.put("price", d.taxIncluded() ? l.priceInclTax() : l.price());
            m.put("amount", l.amount());
            m.put("totalAmount", l.totalAmount());
            m.put("deliveryDate", l.promisedDate() != null ? l.promisedDate() : l.requiredDate());
            m.put("remark", Objects.toString(l.remark(), ""));
            lines.add(m);
        }
        data.put("lines", lines);
        return data;
    }

    // ==================== 工具 ====================

    public SalOrderDO getOrThrow(Long id) {
        SalOrderDO o = id == null ? null : mapper.selectById(id);
        if (o == null) throw new BizException(SalesErrorCodes.ORDER_NOT_EXISTS);
        return o;
    }

    public Map<Long, SalOrderDO> byIds(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Collections.emptyMap();
        return mapper.selectBatchIds(set).stream().collect(Collectors.toMap(SalOrderDO::getId, o -> o));
    }

    public Map<Long, SalOrderLineDO> linesByIds(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Collections.emptyMap();
        return lineMapper.selectBatchIds(set).stream().collect(Collectors.toMap(SalOrderLineDO::getId, l -> l));
    }

    /** 客户转移（SAL-SO-R13）：未完成订单的业务员改为新负责人 */
    public void transferOwner(Collection<Long> customerIds, Long newOwner) {
        for (SalOrderDO o : mapper.selectList(new LambdaQueryWrapper<SalOrderDO>().in(SalOrderDO::getCustomerId, customerIds)
                .in(SalOrderDO::getStatus, DocStatus.DRAFT, DocStatus.PENDING_APPROVAL, DocStatus.APPROVED, DocStatus.IN_PROGRESS))) {
            if (newOwner.equals(o.getOwnerId())) continue;
            Long from = o.getOwnerId();
            support.fillOwner(o, newOwner);
            mapper.updateByIdOrFail(o);
            support.log(BIZ_TYPE, o.getId(), o.getDocNo(), "TRANSFER", "业务员转移", o.getStatus().name(), o.getStatus().name(),
                    support.userName(from) + " → " + support.userName(newOwner));
        }
    }
}
