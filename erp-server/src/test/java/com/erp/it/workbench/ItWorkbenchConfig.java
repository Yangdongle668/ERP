package com.erp.it.workbench;

import com.erp.module.system.api.workflow.ApprovalBizDefinition;
import com.erp.module.workbench.api.dashboard.DashboardCard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 测试用：可审批单据类型、一张加载必然失败的看板卡片（WB-HOME-T04） */
@Configuration
class ItWorkbenchConfig {

    static final String BIZ = "IT_WB_DOC";
    static final String BROKEN_CARD = "IT_BROKEN";

    @Bean
    ApprovalBizDefinition itWbApproval() {
        return ApprovalBizDefinition.of(BIZ, "工作台测试单据", "workbench", "/it/wb/{id}").numberField("amount", "金额");
    }

    @Bean
    DashboardCard itBrokenCard() {
        return DashboardCard.of(BROKEN_CARD, "测试故障卡片", "", 999, null, () -> {
            throw new IllegalStateException("数据源不可用");
        });
    }
}
