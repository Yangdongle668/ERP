package com.erp.module.shipping.service.bi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.bi.api.fact.BiFactProvider;
import com.erp.module.bi.api.fact.BiFacts.SalesFact;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.shipping.dal.dataobject.ShpShipmentDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentLineDO;
import com.erp.module.shipping.dal.mapper.ShpShipmentLineMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentMapper;
import com.erp.module.shipping.service.ShipmentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BI 销售事实中的出货部分（需求 13-01）：出货额（出货日，行不含税金额 × 出货单汇率）、出货数量；
 * 交期达成：订单行首次出货的出货单上计 1 行，首次出货日 ≤ 承诺交期（无则要求交期）计为按期。业务员、部门取销售订单。
 */
@Component
public class ShippingBiFactProvider implements BiFactProvider {

    static final List<String> SHIPPED = List.of(ShipmentStatus.SHIPPED.name(), ShipmentStatus.COMPLETED.name());

    private final ShpShipmentMapper shipmentMapper;
    private final ShpShipmentLineMapper lineMapper;
    private final SalesOrderQueryApi orderQueryApi;

    public ShippingBiFactProvider(ShpShipmentMapper shipmentMapper, ShpShipmentLineMapper lineMapper, SalesOrderQueryApi orderQueryApi) {
        this.shipmentMapper = shipmentMapper;
        this.lineMapper = lineMapper;
        this.orderQueryApi = orderQueryApi;
    }

    @Override
    public List<SalesFact> salesFacts(LocalDate from, LocalDate to) {
        Map<Long, ShpShipmentDO> shipments = shipmentMapper.selectList(new LambdaQueryWrapper<ShpShipmentDO>()
                        .in(ShpShipmentDO::getShipmentStatus, SHIPPED).between(ShpShipmentDO::getShipDate, from, to))
                .stream().collect(Collectors.toMap(ShpShipmentDO::getId, Function.identity()));
        if (shipments.isEmpty()) return List.of();
        List<ShpShipmentLineDO> lines = lineMapper.selectList(new LambdaQueryWrapper<ShpShipmentLineDO>().in(ShpShipmentLineDO::getShipmentId, shipments.keySet()));
        Set<Long> orderLineIds = lines.stream().map(ShpShipmentLineDO::getOrderLineId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SalesOrderLineDTO> orderLines = orderLineIds.isEmpty() ? Map.of() : orderQueryApi.getLines(orderLineIds);
        Map<Long, Long> firstShipment = firstShipments(orderLineIds);
        List<SalesFact> facts = new ArrayList<>();
        for (ShpShipmentLineDO l : lines) {
            ShpShipmentDO s = shipments.get(l.getShipmentId());
            SalesOrderLineDTO ol = l.getOrderLineId() == null ? null : orderLines.get(l.getOrderLineId());
            BigDecimal rate = s.getExchangeRate() == null || s.getExchangeRate().signum() == 0 ? BigDecimal.ONE : s.getExchangeRate();
            BigDecimal amount = nz(l.getAmount()).multiply(rate).setScale(2, RoundingMode.HALF_UP);
            boolean first = l.getOrderLineId() != null && s.getId().equals(firstShipment.get(l.getOrderLineId()));
            boolean onTime = first && ol != null && ol.dueDate() != null && !s.getShipDate().isAfter(ol.dueDate());
            facts.add(new SalesFact(s.getShipDate(), s.getCustomerId(), l.getMaterialId(), ol == null ? s.getOwnerId() : ol.ownerId(),
                    ol == null ? s.getDeptId() : ol.deptId(), BigDecimal.ZERO, amount, nz(l.getBaseQty() != null ? l.getBaseQty() : l.getQty()),
                    BigDecimal.ZERO, first ? 1 : 0, onTime ? 1 : 0, BigDecimal.ZERO));
        }
        return facts;
    }

    /** 订单行 → 首次出货的出货单（最早出货日，同日取 ID 最小） */
    private Map<Long, Long> firstShipments(Set<Long> orderLineIds) {
        Map<Long, Long> result = new HashMap<>();
        if (orderLineIds.isEmpty()) return result;
        List<ShpShipmentLineDO> all = lineMapper.selectList(new LambdaQueryWrapper<ShpShipmentLineDO>().in(ShpShipmentLineDO::getOrderLineId, orderLineIds)
                .select(ShpShipmentLineDO::getShipmentId, ShpShipmentLineDO::getOrderLineId));
        Map<Long, ShpShipmentDO> ships = all.isEmpty() ? Map.of() : shipmentMapper.selectList(new LambdaQueryWrapper<ShpShipmentDO>()
                        .in(ShpShipmentDO::getId, all.stream().map(ShpShipmentLineDO::getShipmentId).collect(Collectors.toSet()))
                        .in(ShpShipmentDO::getShipmentStatus, SHIPPED)).stream().collect(Collectors.toMap(ShpShipmentDO::getId, Function.identity()));
        for (ShpShipmentLineDO l : all) {
            ShpShipmentDO s = ships.get(l.getShipmentId());
            if (s == null || s.getShipDate() == null) continue;
            Long cur = result.get(l.getOrderLineId());
            ShpShipmentDO c = cur == null ? null : ships.get(cur);
            if (c == null || s.getShipDate().isBefore(c.getShipDate()) || (s.getShipDate().isEqual(c.getShipDate()) && s.getId() < c.getId())) {
                result.put(l.getOrderLineId(), s.getId());
            }
        }
        return result;
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
