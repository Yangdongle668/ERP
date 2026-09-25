package com.erp.module.sales.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.sales.api.SalesErrorCodes;
import com.erp.module.sales.api.order.OpenLineFilter;
import com.erp.module.sales.api.order.SalesOrderApi;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderPromisedDateChangedEvent;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.sales.api.order.SalesOrderShipmentChangedEvent;
import com.erp.module.sales.api.order.SalesOrderWritebackApi;
import com.erp.module.sales.config.SalesModuleConfig;
import com.erp.module.sales.controller.vo.OrderVOs.OpenLineRow;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalOrderExecDO;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import com.erp.module.sales.dal.mapper.SalOrderExecMapper;
import com.erp.module.sales.dal.mapper.SalOrderLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.service.SalAction;
import com.erp.module.sales.service.SalStateMachines;
import com.erp.module.sales.service.SalSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 订单执行（需求 04-03 R09～R12）：PMC 交期回复、出货 / 开票 / 收款回写、对外查询。
 * 回写使用乐观锁；出货以出货单 ID 做幂等。
 */
@Service("salOrderExecService")
public class OrderExecService implements SalesOrderApi, SalesOrderQueryApi, SalesOrderWritebackApi {

    public static final String NOTICE = "NOTICE";
    public static final String SHIP = "SHIP";
    public static final String SHIP_REVERSE = "SHIP_REVERSE";
    public static final String BL = "BL";
    public static final String INVOICE = "INVOICE";
    public static final String RECEIPT = "RECEIPT";
    public static final String RETURN = "RETURN";

    private final SalOrderMapper mapper;
    private final SalOrderLineMapper lineMapper;
    private final SalOrderExecMapper execMapper;
    private final OrderService orderService;
    private final PaymentPlanService paymentPlanService;
    private final OpenAmountCalculator openAmountCalculator;
    private final SalSupport support;
    private final DomainEventPublisher eventPublisher;

    public OrderExecService(SalOrderMapper mapper, SalOrderLineMapper lineMapper, SalOrderExecMapper execMapper, OrderService orderService,
                            PaymentPlanService paymentPlanService, OpenAmountCalculator openAmountCalculator, SalSupport support,
                            DomainEventPublisher eventPublisher) {
        this.openAmountCalculator = openAmountCalculator;
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.execMapper = execMapper;
        this.orderService = orderService;
        this.paymentPlanService = paymentPlanService;
        this.support = support;
        this.eventPublisher = eventPublisher;
    }

    // ==================== SalesOrderApi ====================

    /** R09：PMC 交期回复 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePromisedDate(Long lineId, LocalDate date, String remark) {
        SalOrderLineDO l = lineOrThrow(lineId);
        SalOrderDO o = orderService.getOrThrow(l.getOrderId());
        requireActive(o, "回复交期");
        if (OrderService.CLOSED.equals(l.getLineStatus())) throw BizException.of(SalesErrorCodes.ORDER_LINE_CLOSED, o.getDocNo(), l.getLineNo());
        if (date == null || date.isBefore(LocalDate.now())) throw new BizException(SalesErrorCodes.ORDER_PROMISED_PAST);
        LocalDate old = l.getPromisedDate();
        l.setPromisedDate(date);
        l.setPromisedBy(support.currentUser());
        l.setPromisedAt(LocalDateTime.now());
        String text = SalSupport.trim(remark);
        l.setPromiseRemark(text != null && text.length() > 256 ? text.substring(0, 256) : text);
        lineMapper.updateByIdOrFail(l);
        boolean delayed = OrderService.delayed(l);
        o.setDeliveryRisk(lineMapper.selectByParent(o.getId()).stream().anyMatch(OrderService::delayed));
        mapper.updateByIdOrFail(o);
        paymentPlanService.refreshBeforeShipment(o);
        support.log(OrderService.BIZ_TYPE, o.getId(), o.getDocNo(), "PROMISE", "承诺交期", o.getStatus().name(), o.getStatus().name(),
                "第 " + l.getLineNo() + " 行 " + (old == null ? "" : old + " → ") + date + (text == null ? "" : "（" + text + "）"));
        if (delayed && !Objects.equals(old, date)) {
            support.message(List.of(o.getOwnerId()), "订单交期风险",
                    "订单 " + o.getDocNo() + " 第 " + l.getLineNo() + " 行承诺交期 " + date + " 晚于客户要求交期 " + l.getRequiredDate() + "，请与客户确认",
                    "/sales/order/" + o.getId());
        }
        eventPublisher.publish(new SalesOrderPromisedDateChangedEvent(o.getId(), l.getId(), l.getRequiredDate(), date, delayed));
    }

    @Override
    public void validateShipmentQty(Long lineId, BigDecimal baseQty) {
        SalOrderLineDO l = lineOrThrow(lineId);
        SalOrderDO o = orderService.getOrThrow(l.getOrderId());
        requireActive(o, "出货");
        if (OrderService.CLOSED.equals(l.getLineStatus())) throw BizException.of(SalesErrorCodes.ORDER_LINE_CLOSED, o.getDocNo(), l.getLineNo());
        BigDecimal noticeable = noticeable(l);
        if (baseQty.compareTo(noticeable) > 0) {
            throw BizException.of(SalesErrorCodes.ORDER_OVER_NOTICE, o.getDocNo(), l.getLineNo(), SalSupport.plain(noticeable), SalSupport.plain(baseQty));
        }
    }

    private BigDecimal overShipRatio() {
        return BigDecimal.ONE.add(SalSupport.nz(support.params().getDecimal(SalesModuleConfig.P_OVER_SHIP_PCT))
                .divide(SalSupport.HUNDRED, 6, RoundingMode.HALF_UP));
    }

    /** 可通知数量 = 订单数量 × (1 + 超出货比例) − 已通知 */
    BigDecimal noticeable(SalOrderLineDO l) {
        if (OrderService.CLOSED.equals(l.getLineStatus())) return BigDecimal.ZERO;
        return Decimals.qty(l.getBaseQty().multiply(overShipRatio()).subtract(l.getNoticedQty())).max(BigDecimal.ZERO);
    }

    private static void requireActive(SalOrderDO o, String action) {
        if (!OrderService.ACTIVE.contains(o.getStatus())) throw BizException.of(SalesErrorCodes.ORDER_STATUS, o.getStatus().label(), action);
    }

    // ==================== SalesOrderWritebackApi ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onNoticeChanged(Long orderLineId, BigDecimal deltaBaseQty, Long noticeId, String noticeNo) {
        if (deltaBaseQty == null || deltaBaseQty.signum() == 0) return;
        SalOrderLineDO l = lineOrThrow(orderLineId);
        SalOrderDO o = orderService.getOrThrow(l.getOrderId());
        if (deltaBaseQty.signum() > 0) {
            requireActive(o, "出货通知");
            if (OrderService.CLOSED.equals(l.getLineStatus())) throw BizException.of(SalesErrorCodes.ORDER_LINE_CLOSED, o.getDocNo(), l.getLineNo());
            BigDecimal noticeable = noticeable(l);
            if (deltaBaseQty.compareTo(noticeable) > 0) {
                throw BizException.of(SalesErrorCodes.ORDER_OVER_NOTICE, o.getDocNo(), l.getLineNo(), SalSupport.plain(noticeable),
                        SalSupport.plain(deltaBaseQty));
            }
        }
        l.setNoticedQty(Decimals.qty(l.getNoticedQty().add(deltaBaseQty)).max(BigDecimal.ZERO));
        lineMapper.updateByIdOrFail(l);
        exec(o.getId(), l.getId(), NOTICE, "SHIP_NOTICE", noticeId, noticeNo, deltaBaseQty, null, LocalDate.now());
        refreshStatus(o);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onShipped(ShipmentRecord shipment) {
        if (shipment == null || shipment.lines() == null || shipment.lines().isEmpty()) return;
        if (execMapper.selectCount(new LambdaQueryWrapper<SalOrderExecDO>().eq(SalOrderExecDO::getExecType, SHIP).eq(SalOrderExecDO::getDocId, shipment.shipmentId())) > 0) {
            return;
        }
        LocalDate shipDate = shipment.shipDate() == null ? LocalDate.now() : shipment.shipDate();
        Map<Long, SalOrderLineDO> lines = orderService.linesByIds(shipment.lines().stream().map(Line::orderLineId).toList());
        Map<Long, BigDecimal> amountByOrder = new LinkedHashMap<>();
        BigDecimal ratio = overShipRatio();
        for (Line sl : shipment.lines()) {
            SalOrderLineDO l = lines.get(sl.orderLineId());
            if (l == null) throw new BizException(SalesErrorCodes.ORDER_LINE_NOT_EXISTS);
            SalOrderDO o = orderService.getOrThrow(l.getOrderId());
            requireActive(o, "出货");
            BigDecimal shipped = Decimals.qty(l.getShippedQty().add(sl.baseQty()));
            BigDecimal allowed = Decimals.qty(l.getBaseQty().multiply(ratio));
            if (shipped.compareTo(allowed) > 0) {
                throw BizException.of(SalesErrorCodes.ORDER_OVER_SHIP, o.getDocNo(), l.getLineNo(), SalSupport.plain(shipped), SalSupport.plain(allowed));
            }
            l.setShippedQty(shipped);
            if (!OrderService.CLOSED.equals(l.getLineStatus())) {
                l.setLineStatus(shipped.compareTo(l.getBaseQty()) >= 0 ? OrderService.SHIPPED : OrderService.OPEN);
            }
            lineMapper.updateByIdOrFail(l);
            BigDecimal amount = lineAmount(l, sl.baseQty());
            exec(o.getId(), l.getId(), SHIP, "SHIPMENT", shipment.shipmentId(), shipment.shipmentNo(), sl.baseQty(), amount, shipDate);
            amountByOrder.merge(o.getId(), amount, BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : amountByOrder.entrySet()) {
            SalOrderDO o = orderService.getOrThrow(e.getKey());
            o.setShippedAmount(SalSupport.nz(o.getShippedAmount()).add(e.getValue()));
            mapper.updateByIdOrFail(o);
            List<SalOrderLineDO> ls = lineMapper.selectByParent(o.getId());
            boolean fully = ls.stream().noneMatch(x -> OrderService.OPEN.equals(x.getLineStatus()));
            paymentPlanService.onShipped(o, shipment.shipmentId(), shipment.shipmentNo(), shipDate, e.getValue(), fully);
            refreshStatus(o);
            eventPublisher.publish(new SalesOrderShipmentChangedEvent(o.getId(), o.getDocNo(), OrderService.lineInfos(ls)));
            orderService.openAmountChanged(o);
        }
    }

    /** 出货金额（原币含税）= 数量 × 行价税合计 ÷ 行基本单位数量 */
    static BigDecimal lineAmount(SalOrderLineDO l, BigDecimal baseQty) {
        if (l.getBaseQty().signum() == 0) return BigDecimal.ZERO;
        return l.getTotalAmount().multiply(baseQty).divide(l.getBaseQty(), 2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onShipmentReversed(Long shipmentId) {
        if (execMapper.selectCount(new LambdaQueryWrapper<SalOrderExecDO>().eq(SalOrderExecDO::getExecType, SHIP_REVERSE).eq(SalOrderExecDO::getDocId, shipmentId)) > 0) {
            return;
        }
        List<SalOrderExecDO> ships = execMapper.selectList(new LambdaQueryWrapper<SalOrderExecDO>().eq(SalOrderExecDO::getExecType, SHIP)
                .eq(SalOrderExecDO::getDocId, shipmentId));
        if (ships.isEmpty()) return;
        Map<Long, BigDecimal> amountByOrder = new LinkedHashMap<>();
        for (SalOrderExecDO s : ships) {
            SalOrderLineDO l = lineOrThrow(s.getOrderLineId());
            l.setShippedQty(Decimals.qty(l.getShippedQty().subtract(s.getQty())).max(BigDecimal.ZERO));
            if (!OrderService.CLOSED.equals(l.getLineStatus())) {
                l.setLineStatus(l.getShippedQty().compareTo(l.getBaseQty()) >= 0 ? OrderService.SHIPPED : OrderService.OPEN);
            }
            lineMapper.updateByIdOrFail(l);
            exec(s.getOrderId(), l.getId(), SHIP_REVERSE, s.getDocType(), shipmentId, s.getDocNo(), s.getQty().negate(),
                    SalSupport.nz(s.getAmount()).negate(), LocalDate.now());
            amountByOrder.merge(s.getOrderId(), SalSupport.nz(s.getAmount()), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : amountByOrder.entrySet()) {
            SalOrderDO o = orderService.getOrThrow(e.getKey());
            o.setShippedAmount(SalSupport.nz(o.getShippedAmount()).subtract(e.getValue()).max(BigDecimal.ZERO));
            mapper.updateByIdOrFail(o);
            paymentPlanService.onShipmentReversed(o, shipmentId);
            refreshStatus(o);
            eventPublisher.publish(new SalesOrderShipmentChangedEvent(o.getId(), o.getDocNo(), OrderService.lineInfos(lineMapper.selectByParent(o.getId()))));
            orderService.openAmountChanged(o);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onBillOfLading(Long shipmentId, LocalDate blDate) {
        List<SalOrderExecDO> ships = execMapper.selectList(new LambdaQueryWrapper<SalOrderExecDO>().eq(SalOrderExecDO::getExecType, SHIP)
                .eq(SalOrderExecDO::getDocId, shipmentId));
        if (ships.isEmpty()) return;
        paymentPlanService.onBillOfLading(shipmentId, blDate);
        ships.stream().map(SalOrderExecDO::getOrderId).distinct()
                .forEach(oid -> exec(oid, null, BL, "SHIPMENT", shipmentId, ships.get(0).getDocNo(), null, null, blDate));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onInvoiced(Long orderLineId, BigDecimal deltaBaseQty, LocalDate invoiceDate) {
        if (deltaBaseQty == null || deltaBaseQty.signum() == 0) return;
        SalOrderLineDO l = lineOrThrow(orderLineId);
        SalOrderDO o = orderService.getOrThrow(l.getOrderId());
        BigDecimal invoiced = Decimals.qty(l.getInvoicedQty().add(deltaBaseQty));
        if (invoiced.signum() < 0 || invoiced.compareTo(l.getShippedQty()) > 0) {
            throw BizException.of(SalesErrorCodes.ORDER_OVER_INVOICE, o.getDocNo(), l.getLineNo());
        }
        l.setInvoicedQty(invoiced);
        lineMapper.updateByIdOrFail(l);
        LocalDate date = invoiceDate == null ? LocalDate.now() : invoiceDate;
        exec(o.getId(), l.getId(), INVOICE, "INVOICE", null, null, deltaBaseQty, lineAmount(l, deltaBaseQty), date);
        if (deltaBaseQty.signum() > 0) paymentPlanService.onInvoiced(o.getId(), date);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onReceiptAllocated(Long orderId, BigDecimal amount, LocalDate receiptDate) {
        if (amount == null || amount.signum() == 0) return;
        SalOrderDO o = orderService.getOrThrow(orderId);
        paymentPlanService.allocate(orderId, amount);
        o.setReceivedAmount(SalSupport.nz(o.getReceivedAmount()).add(amount));
        mapper.updateByIdOrFail(o);
        exec(orderId, null, RECEIPT, "RECEIPT", null, null, null, amount, receiptDate == null ? LocalDate.now() : receiptDate);
        refreshStatus(orderService.getOrThrow(orderId));
    }

    // ==================== 退货回写（销售退货入库确认） ====================

    /**
     * 退货入库（SAL-SR-R03）：REFUND 累加已退货数量；REPLACE 扣减已出货数量（恢复未出货，订单完成的回到执行中）。
     * qty 为负数表示入库反确认；stockInId / stockInNo 为退货入库单（执行记录）。
     */
    public void onReturned(Long orderLineId, BigDecimal qty, boolean replace, Long stockInId, String stockInNo) {
        SalOrderLineDO l = lineOrThrow(orderLineId);
        SalOrderDO o = orderService.getOrThrow(l.getOrderId());
        if (replace) {
            l.setShippedQty(Decimals.qty(l.getShippedQty().subtract(qty)).max(BigDecimal.ZERO));
            l.setNoticedQty(Decimals.qty(l.getNoticedQty().subtract(qty)).max(BigDecimal.ZERO));
            if (!OrderService.CLOSED.equals(l.getLineStatus())) {
                l.setLineStatus(l.getShippedQty().compareTo(l.getBaseQty()) >= 0 ? OrderService.SHIPPED : OrderService.OPEN);
            }
        } else {
            l.setReturnedQty(Decimals.qty(l.getReturnedQty().add(qty)).max(BigDecimal.ZERO));
        }
        lineMapper.updateByIdOrFail(l);
        exec(o.getId(), l.getId(), RETURN, replace ? "RETURN_REPLACE" : "RETURN_REFUND", stockInId, stockInNo, qty, lineAmount(l, qty), LocalDate.now());
        if (replace) {
            BigDecimal amount = lineAmount(l, qty);
            o.setShippedAmount(SalSupport.nz(o.getShippedAmount()).subtract(amount).max(BigDecimal.ZERO));
            mapper.updateByIdOrFail(o);
            refreshStatus(o);
            eventPublisher.publish(new SalesOrderShipmentChangedEvent(o.getId(), o.getDocNo(), OrderService.lineInfos(lineMapper.selectByParent(o.getId()))));
            orderService.openAmountChanged(o);
        }
    }

    /**
     * 订单状态：已审核 → 执行中（有通知或出货）→ 已完成（所有行出齐或关闭；参数 auto-complete = 否时还需全部回款）；
     * 出货冲销、换货退货时恢复。
     */
    void refreshStatus(SalOrderDO o) {
        if (o.getStatus() != DocStatus.APPROVED && o.getStatus() != DocStatus.IN_PROGRESS && o.getStatus() != DocStatus.COMPLETED) return;
        List<SalOrderLineDO> lines = lineMapper.selectByParent(o.getId());
        boolean any = lines.stream().anyMatch(l -> l.getNoticedQty().signum() > 0 || l.getShippedQty().signum() > 0);
        boolean done = !lines.isEmpty() && lines.stream().noneMatch(l -> OrderService.OPEN.equals(l.getLineStatus()))
                && (support.params().getBool(SalesModuleConfig.P_AUTO_COMPLETE) || paymentPlanService.fullyReceived(o.getId()));
        if (o.getStatus() == DocStatus.COMPLETED && !done) support.fire(SalStateMachines.ORDER, mapper, o, OrderService.BIZ_TYPE, SalAction.REOPEN, null);
        if (o.getStatus() == DocStatus.APPROVED && any) support.fire(SalStateMachines.ORDER, mapper, o, OrderService.BIZ_TYPE, SalAction.START, null);
        if (o.getStatus() == DocStatus.IN_PROGRESS && done) support.fire(SalStateMachines.ORDER, mapper, o, OrderService.BIZ_TYPE, SalAction.COMPLETE, null);
        else if (o.getStatus() == DocStatus.IN_PROGRESS && !any) support.fire(SalStateMachines.ORDER, mapper, o, OrderService.BIZ_TYPE, SalAction.REOPEN, null);
    }

    private void exec(Long orderId, Long lineId, String type, String docType, Long docId, String docNo, BigDecimal qty, BigDecimal amount, LocalDate date) {
        SalOrderExecDO e = new SalOrderExecDO();
        e.setOrderId(orderId);
        e.setOrderLineId(lineId);
        e.setExecType(type);
        e.setDocType(docType);
        e.setDocId(docId);
        e.setDocNo(docNo);
        e.setQty(qty);
        e.setAmount(amount);
        e.setExecDate(date);
        execMapper.insert(e);
    }

    // ==================== SalesOrderQueryApi ====================

    @Override
    public List<SalesOrderLineDTO> getOpenLines(OpenLineFilter filter) {
        OpenLineFilter f = filter == null ? OpenLineFilter.all() : filter;
        List<SalOrderDO> orders = mapper.selectList(new LambdaQueryWrapper<SalOrderDO>().in(SalOrderDO::getStatus, OrderService.ACTIVE)
                .eq(f.customerId() != null, SalOrderDO::getCustomerId, f.customerId())
                .eq(f.orderId() != null, SalOrderDO::getId, f.orderId())
                .eq(f.ownerId() != null, SalOrderDO::getOwnerId, f.ownerId()));
        if (orders.isEmpty()) return List.of();
        Map<Long, SalOrderDO> byId = orders.stream().collect(Collectors.toMap(SalOrderDO::getId, o -> o));
        return lineMapper.selectList(new LambdaQueryWrapper<SalOrderLineDO>().in(SalOrderLineDO::getOrderId, byId.keySet())
                        .eq(SalOrderLineDO::getLineStatus, OrderService.OPEN).eq(f.materialId() != null, SalOrderLineDO::getMaterialId, f.materialId())).stream()
                .filter(l -> l.getBaseQty().compareTo(l.getShippedQty()) > 0)
                .filter(l -> f.dueTo() == null || !OrderService.dueDate(l).isAfter(f.dueTo()))
                .map(l -> dto(byId.get(l.getOrderId()), l))
                .sorted(Comparator.comparing(SalesOrderLineDTO::dueDate).thenComparing(SalesOrderLineDTO::orderNo).thenComparing(SalesOrderLineDTO::lineNo))
                .toList();
    }

    @Override
    public Optional<SalesOrderLineDTO> getLine(Long lineId) {
        SalOrderLineDO l = lineId == null ? null : lineMapper.selectById(lineId);
        if (l == null) return Optional.empty();
        return Optional.of(dto(orderService.getOrThrow(l.getOrderId()), l));
    }

    @Override
    public Map<Long, SalesOrderLineDTO> getLines(Collection<Long> lineIds) {
        Map<Long, SalOrderLineDO> lines = orderService.linesByIds(lineIds);
        Map<Long, SalOrderDO> orders = orderService.byIds(lines.values().stream().map(SalOrderLineDO::getOrderId).toList());
        Map<Long, SalesOrderLineDTO> map = new HashMap<>();
        lines.values().forEach(l -> map.put(l.getId(), dto(orders.get(l.getOrderId()), l)));
        return map;
    }

    SalesOrderLineDTO dto(SalOrderDO o, SalOrderLineDO l) {
        BigDecimal basePrice = l.getBaseQty().signum() == 0 ? l.getPriceInclTax()
                : l.getPriceInclTax().multiply(l.getQty()).divide(l.getBaseQty(), 6, RoundingMode.HALF_UP);
        return new SalesOrderLineDTO(l.getId(), o.getId(), o.getDocNo(), l.getLineNo(), o.getOrderType(), o.getStatus().name(), o.getCustomerId(),
                o.getOwnerId(), o.getDeptId(), o.getCurrency(), l.getMaterialId(), l.getCustomerPartNo(), l.getDescription(), l.getUom(), l.getQty(),
                l.getBaseQty(), l.getPriceInclTax(), basePrice, l.getTaxRate(), l.getRequiredDate(), l.getPromisedDate(), l.getNoticedQty(),
                l.getShippedQty(), l.getReturnedQty(), l.getInvoicedQty(), OrderService.openQty(l), noticeable(l), l.getLineStatus());
    }

    @Override
    public BigDecimal getOpenAmountByCustomer(Long customerId) {
        return openAmounts(List.of(customerId)).getOrDefault(customerId, BigDecimal.ZERO);
    }

    /** 客户未出货订单金额（本位币含税）：补货订单不计 */
    public Map<Long, BigDecimal> openAmounts(Collection<Long> customerIds) {
        return openAmountCalculator.openAmounts(customerIds);
    }

    @Override
    public BigDecimal getUnpaidBeforeShipment(Long orderId) {
        return paymentPlanService.unpaidBeforeShipment(orderId);
    }

    // ==================== 出货选单、提醒 ====================

    public PageResult<OpenLineRow> openLineRows(Long customerId, Long materialId, String orderNo, int pageNo, int pageSize) {
        List<SalesOrderLineDTO> all = getOpenLines(new OpenLineFilter(customerId, materialId, null, null, null)).stream()
                .filter(l -> orderNo == null || orderNo.isBlank() || l.orderNo().startsWith(orderNo.trim().toUpperCase())).toList();
        int from = Math.min((pageNo - 1) * pageSize, all.size());
        List<SalesOrderLineDTO> page = all.subList(from, Math.min(from + pageSize, all.size()));
        Map<Long, MaterialDTO> ms = support.materials(page.stream().map(SalesOrderLineDTO::materialId).toList());
        Map<Long, CustomerDTO> cs = support.customers(page.stream().map(SalesOrderLineDTO::customerId).toList());
        return new PageResult<>(page.stream().map(l -> {
            MaterialDTO m = ms.get(l.materialId());
            return new OpenLineRow(l.lineId(), l.orderId(), l.orderNo(), l.lineNo(), l.customerId(), SalSupport.shortName(cs, l.customerId()),
                    l.materialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), l.uom(), l.qty(), l.baseQty(),
                    l.shippedQty(), l.openQty(), l.noticeableQty(), l.requiredDate(), l.promisedDate(), l.currency(), l.priceInclTax());
        }).toList(), all.size());
    }

    /** R12：承诺交期（无则要求交期）前 N 天仍有未出货数量的订单提醒业务员（每位业务员一条汇总） */
    public int remindDelivery() {
        int days = support.params().getInt(SalesModuleConfig.P_DELIVERY_WARN_DAYS);
        LocalDate limit = LocalDate.now().plusDays(days);
        LocalDate today = LocalDate.now();
        Map<Long, List<SalesOrderLineDTO>> byOwner = getOpenLines(new OpenLineFilter(null, null, null, null, limit)).stream()
                .collect(Collectors.groupingBy(SalesOrderLineDTO::ownerId));
        byOwner.forEach((owner, lines) -> {
            long overdue = lines.stream().filter(l -> l.dueDate().isBefore(today)).count();
            List<String> nos = lines.stream().map(SalesOrderLineDTO::orderNo).distinct().limit(5).toList();
            support.message(List.of(owner), "订单交期提醒", (overdue > 0 ? "已过交期未出货 " + overdue + " 行；" : "") + days + " 天内到期未出货 "
                    + (lines.size() - overdue) + " 行（" + String.join("、", nos) + (nos.size() < lines.stream().map(SalesOrderLineDTO::orderNo)
                    .distinct().count() ? " 等" : "") + "）", "/sales/report?tab=open-orders");
        });
        return byOwner.size();
    }

    private SalOrderLineDO lineOrThrow(Long id) {
        SalOrderLineDO l = id == null ? null : lineMapper.selectById(id);
        if (l == null) throw new BizException(SalesErrorCodes.ORDER_LINE_NOT_EXISTS);
        return l;
    }

    /** 回款流水（给报表）：订单 ID → 期间内收款金额（原币） */
    public Map<Long, BigDecimal> receipts(Collection<Long> orderIds, LocalDate from, LocalDate to) {
        if (orderIds.isEmpty()) return Map.of();
        return execMapper.selectList(new LambdaQueryWrapper<SalOrderExecDO>().in(SalOrderExecDO::getOrderId, orderIds).eq(SalOrderExecDO::getExecType, RECEIPT)
                        .ge(from != null, SalOrderExecDO::getExecDate, from).le(to != null, SalOrderExecDO::getExecDate, to)).stream()
                .collect(Collectors.toMap(SalOrderExecDO::getOrderId, e -> SalSupport.nz(e.getAmount()), BigDecimal::add));
    }

    /** 出货流水（给报表）：订单 ID → 期间内出货金额（原币，扣除冲销） */
    public Map<Long, BigDecimal> shipments(Collection<Long> orderIds, LocalDate from, LocalDate to) {
        if (orderIds.isEmpty()) return Map.of();
        return execMapper.selectList(new LambdaQueryWrapper<SalOrderExecDO>().in(SalOrderExecDO::getOrderId, orderIds)
                        .in(SalOrderExecDO::getExecType, SHIP, SHIP_REVERSE)
                        .ge(from != null, SalOrderExecDO::getExecDate, from).le(to != null, SalOrderExecDO::getExecDate, to)).stream()
                .collect(Collectors.toMap(SalOrderExecDO::getOrderId, e -> SalSupport.nz(e.getAmount()), BigDecimal::add));
    }
}
