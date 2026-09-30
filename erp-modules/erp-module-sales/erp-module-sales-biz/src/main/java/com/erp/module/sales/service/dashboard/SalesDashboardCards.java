package com.erp.module.sales.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import com.erp.module.sales.dal.mapper.SalOrderLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.workbench.api.dashboard.CardData;
import com.erp.module.workbench.api.dashboard.DashboardCard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** 工作台看板卡片（需求 02-01 第 3 节）：本月接单额、在手订单；按数据权限统计（业务员只含自己的订单） */
@Configuration
public class SalesDashboardCards {

    static final List<DocStatus> EFFECTIVE = List.of(DocStatus.APPROVED, DocStatus.IN_PROGRESS, DocStatus.COMPLETED, DocStatus.CLOSED);

    private final SalOrderMapper orderMapper;
    private final SalOrderLineMapper lineMapper;

    public SalesDashboardCards(SalOrderMapper orderMapper, SalOrderLineMapper lineMapper) {
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
    }

    @Bean
    public DashboardCard salOrderMonthCard() {
        return DashboardCard.of("SAL_ORDER_MONTH", "本月接单额", "sales:order:query", 10, "/sales/order", () -> {
            YearMonth ym = YearMonth.now();
            return CardData.amount(orderAmount(ym), null).compare(orderAmount(ym.minusMonths(1)), "环比");
        });
    }

    @Bean
    public DashboardCard salOrderOpenCard() {
        return DashboardCard.of("SAL_ORDER_OPEN", "在手订单", "sales:order:query", 50, "/sales/order", () -> {
            List<SalOrderDO> orders = orderMapper.selectScopedList(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getDeleted, false)
                    .in(SalOrderDO::getStatus, DocStatus.APPROVED, DocStatus.IN_PROGRESS));
            BigDecimal open = BigDecimal.ZERO;
            for (SalOrderDO o : orders) {
                BigDecimal rest = nz(o.getTotalAmount()).subtract(nz(o.getShippedAmount()));
                if (rest.signum() > 0) open = open.add(rest.multiply(o.getExchangeRate() == null ? BigDecimal.ONE : o.getExchangeRate()));
            }
            long lines = orders.isEmpty() ? 0 : lineMapper.selectList(new LambdaQueryWrapper<SalOrderLineDO>()
                            .in(SalOrderLineDO::getOrderId, orders.stream().map(SalOrderDO::getId).toList())).stream()
                    .filter(l -> nz(l.getShippedQty()).compareTo(nz(l.getQty())) < 0).count();
            return CardData.amount(open.setScale(2, java.math.RoundingMode.HALF_UP), orders.size() + " 张订单，" + lines + " 行未出货");
        });
    }

    private BigDecimal orderAmount(YearMonth ym) {
        LocalDate from = ym.atDay(1);
        return orderMapper.selectScopedList(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getDeleted, false).in(SalOrderDO::getStatus, EFFECTIVE)
                        .ge(SalOrderDO::getApprovedAt, from.atStartOfDay()).lt(SalOrderDO::getApprovedAt, from.plusMonths(1).atStartOfDay()))
                .stream().map(o -> nz(o.getTotalAmountBase())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
