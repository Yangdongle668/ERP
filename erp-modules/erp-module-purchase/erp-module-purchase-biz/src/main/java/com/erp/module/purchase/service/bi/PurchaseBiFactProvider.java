package com.erp.module.purchase.service.bi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.bi.api.fact.BiFactProvider;
import com.erp.module.bi.api.fact.BiFacts.PurchaseFact;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import com.erp.module.purchase.dal.dataobject.ReceiptDO;
import com.erp.module.purchase.dal.dataobject.ReceiptLineDO;
import com.erp.module.purchase.dal.mapper.OrderLineMapper;
import com.erp.module.purchase.dal.mapper.OrderMapper;
import com.erp.module.purchase.dal.mapper.ReceiptLineMapper;
import com.erp.module.purchase.dal.mapper.ReceiptMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BI 采购事实（需求 13-01）：采购额（审核订单的订单日期，行价税合计 × 汇率）、到货额（入库日，合格 + 让步数量 × 不含税单价 × 汇率）、
 * 准时率（到期日 = 确认交期，无则需求日期；首次到货日 ≤ 到期日为准时）。采购员取订单负责人。
 */
@Component
public class PurchaseBiFactProvider implements BiFactProvider {

    static final List<DocStatus> EFFECTIVE = List.of(DocStatus.APPROVED, DocStatus.IN_PROGRESS, DocStatus.COMPLETED, DocStatus.CLOSED);

    private final OrderMapper orderMapper;
    private final OrderLineMapper lineMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptLineMapper receiptLineMapper;

    public PurchaseBiFactProvider(OrderMapper orderMapper, OrderLineMapper lineMapper, ReceiptMapper receiptMapper, ReceiptLineMapper receiptLineMapper) {
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
        this.receiptMapper = receiptMapper;
        this.receiptLineMapper = receiptLineMapper;
    }

    @Override
    public List<PurchaseFact> purchaseFacts(LocalDate from, LocalDate to) {
        List<PurchaseFact> facts = new ArrayList<>();
        Map<Long, OrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderDO>().in(OrderDO::getStatus, EFFECTIVE).between(OrderDO::getDocDate, from, to))
                .stream().collect(Collectors.toMap(OrderDO::getId, Function.identity()));
        if (!orders.isEmpty()) {
            for (OrderLineDO l : lineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>().in(OrderLineDO::getOrderId, orders.keySet()))) {
                OrderDO o = orders.get(l.getOrderId());
                facts.add(new PurchaseFact(o.getDocDate(), o.getSupplierId(), l.getMaterialId(), o.getOwnerId(), o.getDeptId(),
                        nz(l.getTotalAmount()).multiply(rate(o)).setScale(2, RoundingMode.HALF_UP), nz(l.getBaseQty()), BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
            }
        }
        // 到货：入库日在区间内的收货行
        List<ReceiptLineDO> rls = receiptLineMapper.selectList(new LambdaQueryWrapper<ReceiptLineDO>().between(ReceiptLineDO::getStockedDate, from, to)
                .isNotNull(ReceiptLineDO::getOrderLineId));
        // 到期：到期日在区间内的订单行
        List<OrderLineDO> due = lineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>().and(w -> w.between(OrderLineDO::getConfirmedDate, from, to)
                .or(x -> x.isNull(OrderLineDO::getConfirmedDate).between(OrderLineDO::getRequiredDate, from, to))));
        Set<Long> lineIds = new HashSet<>();
        rls.forEach(r -> lineIds.add(r.getOrderLineId()));
        Map<Long, OrderLineDO> lines = lineIds.isEmpty() ? Map.of() : lineMapper.selectBatchIds(lineIds).stream()
                .collect(Collectors.toMap(OrderLineDO::getId, Function.identity()));
        Set<Long> orderIds = new HashSet<>();
        lines.values().forEach(l -> orderIds.add(l.getOrderId()));
        due.forEach(l -> orderIds.add(l.getOrderId()));
        Map<Long, OrderDO> allOrders = orderIds.isEmpty() ? Map.of() : orderMapper.selectBatchIds(orderIds).stream()
                .filter(o -> EFFECTIVE.contains(o.getStatus())).collect(Collectors.toMap(OrderDO::getId, Function.identity()));
        Map<Long, ReceiptDO> receipts = rls.isEmpty() ? Map.of() : receiptMapper.selectBatchIds(rls.stream().map(ReceiptLineDO::getReceiptId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(ReceiptDO::getId, Function.identity()));
        for (ReceiptLineDO r : rls) {
            OrderLineDO l = lines.get(r.getOrderLineId());
            OrderDO o = l == null ? null : allOrders.get(l.getOrderId());
            if (o == null) continue;
            BigDecimal qty = nz(r.getQualifiedQty()).add(nz(r.getConcessionQty()));
            if (qty.signum() == 0 && !Boolean.TRUE.equals(r.getInspectRequired())) qty = nz(r.getStockedQty());
            BigDecimal price = nz(l.getQty()).signum() == 0 ? BigDecimal.ZERO : nz(l.getAmount()).divide(l.getQty(), 6, RoundingMode.HALF_UP);
            ReceiptDO h = receipts.get(r.getReceiptId());
            facts.add(new PurchaseFact(r.getStockedDate(), h != null && h.getSupplierId() != null ? h.getSupplierId() : o.getSupplierId(), r.getMaterialId(),
                    o.getOwnerId(), o.getDeptId(), BigDecimal.ZERO, BigDecimal.ZERO, qty.multiply(price).multiply(rate(o)).setScale(2, RoundingMode.HALF_UP),
                    qty, 0, 0));
        }
        for (OrderLineDO l : due) {
            OrderDO o = allOrders.get(l.getOrderId());
            if (o == null) continue;
            LocalDate d = l.getConfirmedDate() != null ? l.getConfirmedDate() : l.getRequiredDate();
            boolean onTime = l.getFirstReceivedDate() != null && !l.getFirstReceivedDate().isAfter(d);
            facts.add(new PurchaseFact(d, o.getSupplierId(), l.getMaterialId(), o.getOwnerId(), o.getDeptId(), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 1, onTime ? 1 : 0));
        }
        return facts.stream().filter(Objects::nonNull).toList();
    }

    static BigDecimal rate(OrderDO o) {
        return o.getExchangeRate() == null || o.getExchangeRate().signum() == 0 ? BigDecimal.ONE : o.getExchangeRate();
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
