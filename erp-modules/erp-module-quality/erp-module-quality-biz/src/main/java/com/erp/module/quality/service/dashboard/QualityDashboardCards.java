package com.erp.module.quality.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.quality.dal.dataobject.QcInspectionDO;
import com.erp.module.quality.dal.mapper.QcInspectionMapper;
import com.erp.module.quality.service.InspStatus;
import com.erp.module.quality.service.inspection.InspectionService;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.workbench.api.dashboard.CardData;
import com.erp.module.workbench.api.dashboard.DashboardCard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/** 工作台看板卡片（需求 02-01 第 3 节）：待检 / 超时待检（IQC）、来料合格率（本月 IQC 判定批次） */
@Configuration
public class QualityDashboardCards {

    static final String IQC = "IQC";

    private final QcInspectionMapper inspectionMapper;
    private final ParamApi paramApi;

    public QualityDashboardCards(QcInspectionMapper inspectionMapper, ParamApi paramApi) {
        this.inspectionMapper = inspectionMapper;
        this.paramApi = paramApi;
    }

    @Bean
    public DashboardCard qcPendingCard() {
        return DashboardCard.of("QC_IQC_PENDING", "待检 / 超时待检", "qc:iqc:query", 100, "/quality/iqc", () -> {
            List<QcInspectionDO> list = inspectionMapper.selectList(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getInspectType, IQC)
                    .in(QcInspectionDO::getInspStatus, InspStatus.PENDING.name(), InspStatus.INSPECTING.name())
                    .select(QcInspectionDO::getId, QcInspectionDO::getCreatedAt));
            int hours = Math.max(1, paramApi.getInt(QualityModuleConfig.P_OVERDUE_HOURS));
            LocalDateTime limit = LocalDateTime.now().minusHours(hours);
            long overdue = list.stream().filter(d -> d.getCreatedAt() != null && d.getCreatedAt().isBefore(limit)).count();
            return CardData.count(list.size(), "超时 " + overdue + " 张（超过 " + hours + " 小时）");
        });
    }

    @Bean
    public DashboardCard qcIqcPassRateCard() {
        return DashboardCard.of("QC_IQC_PASS_RATE", "来料合格率", "qc:report:query", 110, "/quality/report", () -> {
            YearMonth ym = YearMonth.now();
            BigDecimal rate = passRate(ym);
            return CardData.percent(rate, rate == null ? "本月暂无判定批次" : null).compare(passRate(ym.minusMonths(1)), "环比");
        });
    }

    private BigDecimal passRate(YearMonth ym) {
        List<QcInspectionDO> judged = inspectionMapper.selectList(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getInspectType, IQC)
                .ne(QcInspectionDO::getInspStatus, InspStatus.CANCELED.name()).isNotNull(QcInspectionDO::getResult)
                .ge(QcInspectionDO::getJudgeAt, ym.atDay(1).atStartOfDay()).lt(QcInspectionDO::getJudgeAt, ym.plusMonths(1).atDay(1).atStartOfDay())
                .select(QcInspectionDO::getId, QcInspectionDO::getResult));
        if (judged.isEmpty()) return null;
        long pass = judged.stream().filter(d -> InspectionService.PASS.equals(d.getResult())).count();
        return BigDecimal.valueOf(pass * 100).divide(BigDecimal.valueOf(judged.size()), 1, RoundingMode.HALF_UP);
    }
}
