package com.erp.module.purchase.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import com.erp.module.purchase.dal.mapper.OrderLineMapper;
import com.erp.module.purchase.dal.mapper.OrderMapper;
import com.erp.module.workbench.api.dashboard.CardData;
import com.erp.module.workbench.api.dashboard.DashboardCard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 工作台看板卡片（需求 02-01 第 3 节）：逾期未到货（确认交期 / 需求日期早于今天且未收齐的订单行，按数据权限） */
@Configuration
public class PurchaseDashboardCards {

    private final OrderMapper orderMapper;
    private final OrderLineMapper lineMapper;

    public PurchaseDashboardCards(OrderMapper orderMapper, OrderLineMapper lineMapper) {
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
    }

    @Bean
    public DashboardCard purOverdueCard() {
        return DashboardCard.of("PUR_ORDER_OVERDUE", "逾期未到货", "pur:order:query", 90, "/purchase/order", () -> {
            Map<Long, OrderDO> orders = orderMapper.selectScopedList(new LambdaQueryWrapper<OrderDO>().eq(OrderDO::getDeleted, false)
                    .in(OrderDO::getStatus, DocStatus.APPROVED, DocStatus.IN_PROGRESS)).stream().collect(Collectors.toMap(OrderDO::getId, Function.identity()));
            if (orders.isEmpty()) return CardData.amount(BigDecimal.ZERO, "0 行");
            LocalDate today = LocalDate.now();
            BigDecimal amount = BigDecimal.ZERO;
            long lines = 0;
            for (OrderLineDO l : lineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>().in(OrderLineDO::getOrderId, orders.keySet()))) {
                LocalDate due = l.getConfirmedDate() != null ? l.getConfirmedDate() : l.getRequiredDate();
                BigDecimal open = nz(l.getQty()).subtract(nz(l.getReceivedQty()));
                if (due == null || !due.isBefore(today) || open.signum() <= 0 || "CLOSED".equals(l.getLineStatus())) continue;
                OrderDO o = orders.get(l.getOrderId());
                BigDecimal price = nz(l.getQty()).signum() == 0 ? BigDecimal.ZERO : nz(l.getTotalAmount()).divide(l.getQty(), 6, RoundingMode.HALF_UP);
                amount = amount.add(open.multiply(price).multiply(o.getExchangeRate() == null ? BigDecimal.ONE : o.getExchangeRate()));
                lines++;
            }
            return CardData.amount(amount.setScale(2, RoundingMode.HALF_UP), lines + " 行").asCost();
        });
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
