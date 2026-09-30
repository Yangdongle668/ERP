package com.erp.module.production.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.production.dal.dataobject.MfgFinishDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgReportDO;
import com.erp.module.production.dal.mapper.MfgFinishMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgReportMapper;
import com.erp.module.production.service.ProdStatus;
import com.erp.module.workbench.api.dashboard.CardData;
import com.erp.module.workbench.api.dashboard.DashboardCard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** 工作台看板卡片（需求 02-01 第 3 节）：生产进度（在制、今日完工、延期）、今日一次良率（按数据权限） */
@Configuration
public class ProductionDashboardCards {

    static final List<String> WIP = List.of(ProdStatus.RELEASED.name(), ProdStatus.IN_PROGRESS.name(), ProdStatus.SUSPENDED.name());

    private final MfgProdOrderMapper orderMapper;
    private final MfgReportMapper reportMapper;
    private final MfgFinishMapper finishMapper;

    public ProductionDashboardCards(MfgProdOrderMapper orderMapper, MfgReportMapper reportMapper, MfgFinishMapper finishMapper) {
        this.orderMapper = orderMapper;
        this.reportMapper = reportMapper;
        this.finishMapper = finishMapper;
    }

    @Bean
    public DashboardCard mfgProgressCard() {
        return DashboardCard.of("MFG_PROGRESS", "生产进度", "mfg:prod-order:query", 120, "/production/prod-order", () -> {
            LocalDate today = LocalDate.now();
            List<MfgProdOrderDO> wip = orderMapper.selectScopedList(new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getDeleted, false)
                    .in(MfgProdOrderDO::getProdStatus, WIP));
            long delayed = wip.stream().filter(o -> o.getPlanEnd() != null && o.getPlanEnd().isBefore(today)).count();
            BigDecimal finished = finishMapper.selectList(new LambdaQueryWrapper<MfgFinishDO>().eq(MfgFinishDO::getDocDate, today)
                            .ne(MfgFinishDO::getStatus, DocStatus.VOIDED)).stream()
                    .map(f -> f.getQty() == null ? BigDecimal.ZERO : f.getQty()).reduce(BigDecimal.ZERO, BigDecimal::add);
            return CardData.count(wip.size(), "今日完工 " + finished.stripTrailingZeros().toPlainString() + "，延期 " + delayed + " 张");
        });
    }

    @Bean
    public DashboardCard mfgYieldCard() {
        return DashboardCard.of("MFG_YIELD_TODAY", "今日良率", "mfg:defect:query", 130, "/production/report-center", () -> {
            BigDecimal[] today = totals(LocalDate.now());
            BigDecimal[] yesterday = totals(LocalDate.now().minusDays(1));
            return CardData.percent(rate(today), today[1].signum() == 0 ? "今日暂无报工" : "合格 " + today[0].stripTrailingZeros().toPlainString()
                    + " / 报工 " + today[1].stripTrailingZeros().toPlainString()).compare(rate(yesterday), "较昨日");
        });
    }

    /** [合格数, 报工总数（合格 + 不良 + 报废）] */
    private BigDecimal[] totals(LocalDate day) {
        BigDecimal good = BigDecimal.ZERO;
        BigDecimal all = BigDecimal.ZERO;
        for (MfgReportDO r : reportMapper.selectScopedList(new LambdaQueryWrapper<MfgReportDO>().eq(MfgReportDO::getDeleted, false)
                .eq(MfgReportDO::getReportDate, day).eq(MfgReportDO::getStatus, DocStatus.APPROVED))) {
            BigDecimal g = nz(r.getGoodQty());
            good = good.add(g);
            all = all.add(g).add(nz(r.getDefectQty())).add(nz(r.getScrapQty()));
        }
        return new BigDecimal[]{good, all};
    }

    private static BigDecimal rate(BigDecimal[] t) {
        return t[1].signum() == 0 ? null : t[0].multiply(BigDecimal.valueOf(100)).divide(t[1], 1, RoundingMode.HALF_UP);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
