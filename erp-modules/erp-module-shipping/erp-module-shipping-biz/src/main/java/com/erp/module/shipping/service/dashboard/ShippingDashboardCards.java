package com.erp.module.shipping.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.shipping.dal.dataobject.ShpShipmentDO;
import com.erp.module.shipping.dal.mapper.ShpShipmentMapper;
import com.erp.module.shipping.service.ShipmentStatus;
import com.erp.module.workbench.api.dashboard.CardData;
import com.erp.module.workbench.api.dashboard.DashboardCard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** 工作台看板卡片（需求 02-01 第 3 节）：本月出货额、近 6 个月出货趋势（出库确认后的出货单，本位币，按数据权限） */
@Configuration
public class ShippingDashboardCards {

    private final ShpShipmentMapper shipmentMapper;

    public ShippingDashboardCards(ShpShipmentMapper shipmentMapper) {
        this.shipmentMapper = shipmentMapper;
    }

    @Bean
    public DashboardCard shpMonthCard() {
        return DashboardCard.of("SHP_SHIPMENT_MONTH", "本月出货额", "shp:shipment:query", 20, "/shipping/shipment", () -> {
            YearMonth ym = YearMonth.now();
            return CardData.amount(amount(ym), null).compare(amount(ym.minusMonths(1)), "环比");
        });
    }

    @Bean
    public DashboardCard shpTrendCard() {
        return DashboardCard.chart("SHP_SHIPMENT_TREND", "近 6 个月出货趋势", "shp:shipment:query", 900, "/shipping/report", () -> {
            List<CardData.CardPoint> points = new ArrayList<>();
            YearMonth now = YearMonth.now();
            for (int i = 5; i >= 0; i--) {
                YearMonth ym = now.minusMonths(i);
                points.add(new CardData.CardPoint(ym.toString(), amount(ym)));
            }
            return CardData.chart(points);
        });
    }

    private BigDecimal amount(YearMonth ym) {
        return shipmentMapper.selectScopedList(new LambdaQueryWrapper<ShpShipmentDO>().eq(ShpShipmentDO::getDeleted, false)
                        .in(ShpShipmentDO::getShipmentStatus, ShipmentStatus.SHIPPED.name(), ShipmentStatus.COMPLETED.name())
                        .between(ShpShipmentDO::getShipDate, ym.atDay(1), ym.atEndOfMonth()))
                .stream().map(s -> s.getTotalAmountBase() == null ? BigDecimal.ZERO : s.getTotalAmountBase()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
