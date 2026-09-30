package com.erp.module.bi.config;

import com.erp.framework.module.ErpModule;
import com.erp.module.system.api.param.ParamDefinition;
import com.erp.module.system.api.param.ParamDefinitions;
import com.erp.module.system.api.permission.PermissionDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** BI / AI 模块的声明式注册（需求 13-BI与AI README 第 5、6 节） */
@Configuration
public class BiModuleConfig {

    public static final String MODULE = "bi";

    public static final String PERM_DASHBOARD = "bi:dashboard:view";
    public static final String PERM_SALES = "bi:sales:view";
    public static final String PERM_PURCHASE = "bi:purchase:view";
    public static final String PERM_INVENTORY = "bi:inventory:view";
    public static final String PERM_PRODUCTION = "bi:production:view";
    public static final String PERM_QUALITY = "bi:quality:view";
    public static final String PERM_FINANCE = "bi:finance:view";
    public static final String PERM_METRIC = "bi:metric:manage";
    public static final String PERM_SUBSCRIPTION = "bi:subscription:manage";
    public static final String PERM_EXPORT = "bi:export";
    public static final String PERM_AI_USE = "ai:query:use";
    public static final String PERM_AI_LOG = "ai:log:view";
    public static final String PERM_AI_SETTING = "ai:setting:manage";

    public static final String P_NIGHTLY_TIME = "bi.etl.nightly-time";
    public static final String P_FISCAL_START = "bi.fiscal.year-start-month";
    public static final String P_SLOW_DAYS = "bi.inventory.slow-moving-days";
    public static final String P_AI_ENABLED = "ai.enabled";
    public static final String P_AI_PROVIDER = "ai.provider";
    public static final String P_AI_MODEL = "ai.model";
    public static final String P_AI_KEY = "ai.api-key";
    public static final String P_AI_MASK = "ai.mask-sensitive";
    public static final String P_AI_QUOTA = "ai.daily-quota-per-user";

    /** 默认模型 */
    public static final String DEFAULT_MODEL = "claude-opus-5-5";

    @Bean
    public ErpModule biModule() {
        return new ErpModule(MODULE, "BI/AI", 400);
    }

    @Bean
    public PermissionDefinition biPermissions() {
        return PermissionDefinition.group(MODULE, "bi", "经营分析", 10)
                .menu(PERM_DASHBOARD, "经营驾驶舱")
                .menu(PERM_SALES, "销售分析")
                .menu(PERM_PURCHASE, "采购分析")
                .menu(PERM_INVENTORY, "库存分析")
                .menu(PERM_PRODUCTION, "生产分析")
                .menu(PERM_QUALITY, "品质分析")
                .menu(PERM_FINANCE, "财务分析（含毛利、应收）")
                .menu(PERM_METRIC, "指标库与数据任务")
                .button(PERM_SUBSCRIPTION, "报表订阅")
                .button(PERM_EXPORT, "导出");
    }

    @Bean
    public PermissionDefinition aiPermissions() {
        return PermissionDefinition.group(MODULE, "ai", "AI 分析", 20)
                .menu(PERM_AI_USE, "AI 问数")
                .button(PERM_AI_LOG, "问答日志", PERM_AI_USE)
                .button(PERM_AI_SETTING, "AI 设置与用量", PERM_AI_USE);
    }

    @Bean
    public ParamDefinitions biParams() {
        return ParamDefinitions.of(
                ParamDefinition.time(P_NIGHTLY_TIME, MODULE, "数据", "全量校对时间", "03:00",
                        "说明用；实际执行时间以定时任务 BI_NIGHTLY_RECONCILE 的 Cron 为准").sort(10),
                ParamDefinition.integer(P_FISCAL_START, MODULE, "口径", "财年起始月", 1, 1, 12, "“本年”期间从该月开始").sort(10),
                ParamDefinition.integer(P_SLOW_DAYS, MODULE, "口径", "呆滞天数", 180, 30, 3650, "超过该天数无出库（从无出库按最近入库）的库存计为呆滞").sort(20),
                ParamDefinition.bool(P_AI_ENABLED, MODULE, "AI", "启用 AI 分析", false, "").sort(10),
                ParamDefinition.enumOf(P_AI_PROVIDER, MODULE, "AI", "大模型供应商", "ANTHROPIC",
                        List.of(new ParamDefinition.Option("ANTHROPIC", "Anthropic Messages API")), "适配器可扩展（LlmAdapter）").sort(20),
                ParamDefinition.string(P_AI_MODEL, MODULE, "AI", "模型", DEFAULT_MODEL, "").sort(30),
                ParamDefinition.string(P_AI_KEY, MODULE, "AI", "API Key", "",
                        "页面只显示后 4 位；也可用环境变量 ERP_AI_API_KEY 注入（优先使用参数）").sort(40),
                ParamDefinition.bool(P_AI_MASK, MODULE, "AI", "敏感字段脱敏", true, "成本、价格、毛利额等数值不发送给模型，只发送比例、排名和变化率").sort(50),
                ParamDefinition.integer(P_AI_QUOTA, MODULE, "AI", "每用户每日提问上限", 50, 1, 10000, "").sort(60));
    }
}
