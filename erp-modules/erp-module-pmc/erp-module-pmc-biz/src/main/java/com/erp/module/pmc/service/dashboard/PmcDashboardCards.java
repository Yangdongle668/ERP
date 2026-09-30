package com.erp.module.pmc.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.pmc.dal.dataobject.PmcDemandDO;
import com.erp.module.pmc.dal.dataobject.PmcMrpResultDO;
import com.erp.module.pmc.dal.dataobject.PmcShortageOrderDO;
import com.erp.module.pmc.dal.mapper.PmcDemandMapper;
import com.erp.module.pmc.dal.mapper.PmcMrpResultMapper;
import com.erp.module.pmc.dal.mapper.PmcShortageOrderMapper;
import com.erp.module.workbench.api.dashboard.CardData;
import com.erp.module.workbench.api.dashboard.DashboardCard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 工作台看板卡片（需求 02-01 第 3 节）：交期达成率、待转 MRP 建议、缺料订单。
 * 交期达成率 = 本月已到承诺交期（无承诺时按客户交期）的销售订单需求中已全部满足的行数占比。
 */
@Configuration
public class PmcDashboardCards {

    private final PmcDemandMapper demandMapper;
    private final PmcMrpResultMapper resultMapper;
    private final PmcShortageOrderMapper shortageOrderMapper;

    public PmcDashboardCards(PmcDemandMapper demandMapper, PmcMrpResultMapper resultMapper, PmcShortageOrderMapper shortageOrderMapper) {
        this.demandMapper = demandMapper;
        this.resultMapper = resultMapper;
        this.shortageOrderMapper = shortageOrderMapper;
    }

    @Bean
    public DashboardCard pmcOnTimeCard() {
        return DashboardCard.of("PMC_ON_TIME_RATE", "交期达成率", "pmc:alert:query", 60, "/pmc/alert", () -> {
            LocalDate today = LocalDate.now();
            LocalDate from = today.withDayOfMonth(1);
            List<PmcDemandDO> due = demandMapper.selectList(new LambdaQueryWrapper<PmcDemandDO>().eq(PmcDemandDO::getDemandType, "SALES_ORDER")
                    .and(w -> w.between(PmcDemandDO::getPromisedDate, from, today)
                            .or(x -> x.isNull(PmcDemandDO::getPromisedDate).between(PmcDemandDO::getCustomerDate, from, today))));
            long done = due.stream().filter(d -> d.getOpenQty() == null || d.getOpenQty().signum() <= 0).count();
            BigDecimal rate = due.isEmpty() ? null : BigDecimal.valueOf(done * 100).divide(BigDecimal.valueOf(due.size()), 1, RoundingMode.HALF_UP);
            return CardData.percent(rate, due.isEmpty() ? "本月暂无到期需求" : "到期 " + due.size() + " 行，未满足 " + (due.size() - done) + " 行");
        });
    }

    @Bean
    public DashboardCard pmcMrpPendingCard() {
        return DashboardCard.of("PMC_MRP_PENDING", "待转 MRP 建议", "pmc:mrp:query", 70, "/pmc/mrp/suggestions", () -> {
            List<PmcMrpResultDO> list = resultMapper.selectList(new LambdaQueryWrapper<PmcMrpResultDO>().eq(PmcMrpResultDO::getSuggestionStatus, "PENDING")
                    .select(PmcMrpResultDO::getId, PmcMrpResultDO::getSuggestionType, PmcMrpResultDO::getIsLate));
            long purchase = list.stream().filter(r -> "PURCHASE".equals(r.getSuggestionType()) || "OUTSOURCE".equals(r.getSuggestionType())).count();
            long late = list.stream().filter(r -> Boolean.TRUE.equals(r.getIsLate())).count();
            return CardData.count(list.size(), "采购 " + purchase + "，生产 " + (list.size() - purchase) + (late > 0 ? "，已延误 " + late : ""));
        });
    }

    @Bean
    public DashboardCard pmcShortageCard() {
        return DashboardCard.of("PMC_SHORTAGE_ORDERS", "缺料订单", "pmc:shortage:query", 80, "/pmc/shortage", () -> {
            String latest = shortageOrderMapper.selectList(new LambdaQueryWrapper<PmcShortageOrderDO>().select(PmcShortageOrderDO::getSnapshotNo)
                    .orderByDesc(PmcShortageOrderDO::getSnapshotNo).last("LIMIT 1")).stream().findFirst().map(PmcShortageOrderDO::getSnapshotNo).orElse(null);
            if (latest == null) return CardData.count(0, "尚未进行缺料分析");
            List<PmcShortageOrderDO> orders = shortageOrderMapper.selectList(new LambdaQueryWrapper<PmcShortageOrderDO>().eq(PmcShortageOrderDO::getSnapshotNo, latest));
            long shortCount = orders.stream().filter(o -> o.getShortLineCount() != null && o.getShortLineCount() > 0).count();
            return CardData.count(shortCount, "共分析 " + orders.size() + " 张订单").asCost();
        });
    }
}
