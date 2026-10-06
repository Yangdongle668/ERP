package com.erp.module.pmc.config;

import com.erp.framework.module.ErpModule;
import com.erp.module.system.api.coderule.CodeRuleDefinition;
import com.erp.module.system.api.coderule.CodeRuleDefinition.ResetCycle;
import com.erp.module.system.api.dict.DictDefinition;
import com.erp.module.system.api.param.ParamDefinition;
import com.erp.module.system.api.param.ParamDefinitions;
import com.erp.module.system.api.permission.PermissionDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** PMC 模块的声明式注册（需求 06-PMC README 第 6、7、9 节） */
@Configuration
public class PmcModuleConfig {

    public static final String MODULE = "pmc";

    // 编码规则 / 单据类型（操作日志共用）
    public static final String MPS = "PMC_MPS";
    public static final String MRP_RUN = "PMC_MRP_RUN";
    public static final String SHIPPING_PLAN = "PMC_SHIPPING_PLAN";

    // 系统参数
    public static final String P_HORIZON = "pmc.mrp.horizon-days";
    public static final String P_USE_MPS = "pmc.mrp.use-mps";
    public static final String P_INCLUDE_FORECAST = "pmc.mrp.include-forecast";
    public static final String P_INCLUDE_SAFETY = "pmc.mrp.include-safety-stock";
    public static final String P_USE_SUBSTITUTE = "pmc.mrp.use-substitute";
    public static final String P_PO_DATE_BASIS = "pmc.mrp.po-date-basis";
    public static final String P_LEAD_TIME_BASIS = "pmc.lead-time.basis";
    public static final String P_NIGHTLY = "pmc.mrp.nightly";
    public static final String P_TOLERANCE = "pmc.mrp.reschedule-tolerance-days";
    public static final String P_ALERT_LEVELS = "pmc.alert.levels";
    public static final String P_SCHEDULE_MODE = "pmc.schedule.mode";
    public static final String P_ALERT_MANAGERS = "pmc.alert.managers";

    @Bean
    public ErpModule pmcModule() {
        return new ErpModule(MODULE, "PMC", 230);
    }

    // ==================== 编码规则 ====================

    @Bean
    public CodeRuleDefinition pmcMpsCodeRule() {
        return CodeRuleDefinition.of(MPS, "MPS", MODULE, "MPS-", "yyyyMM", "-", 3, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition pmcMrpRunCodeRule() {
        return CodeRuleDefinition.of(MRP_RUN, "MRP 运算", MODULE, "MRP-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition pmcShippingPlanCodeRule() {
        return CodeRuleDefinition.of(SHIPPING_PLAN, "出货计划", MODULE, "SP-", "yyyyMM", "-", 3, ResetCycle.MONTH);
    }

    // ==================== 权限点 ====================

    @Bean
    public PermissionDefinition pmcDemandPermissions() {
        return PermissionDefinition.group(MODULE, "demand", "需求池与交期回复", 10)
                .menu("pmc:demand:query", "查看")
                .button("pmc:demand:create", "手工需求")
                .button("pmc:delivery:reply", "交期回复");
    }

    @Bean
    public PermissionDefinition pmcMpsPermissions() {
        return PermissionDefinition.group(MODULE, "mps", "MPS", 20)
                .menu("pmc:mps:query", "查看")
                .button("pmc:mps:create", "新建")
                .button("pmc:mps:update", "编辑")
                .button("pmc:mps:publish", "发布/关闭");
    }

    @Bean
    public PermissionDefinition pmcMrpPermissions() {
        return PermissionDefinition.group(MODULE, "mrp", "MRP", 30)
                .menu("pmc:mrp:query", "查看")
                .button("pmc:mrp:run", "运算")
                .button("pmc:mrp:convert", "建议转单/推送例外")
                .button("pmc:mrp:ignore", "忽略建议");
    }

    @Bean
    public PermissionDefinition pmcSchedulePermissions() {
        return PermissionDefinition.group(MODULE, "schedule", "排产", 40)
                .menu("pmc:schedule:query", "查看")
                .button("pmc:schedule:run", "运行排产")
                .button("pmc:schedule:adjust", "调整/锁定")
                .button("pmc:schedule:apply", "应用到生产订单");
    }

    @Bean
    public PermissionDefinition pmcCapacityPermissions() {
        return PermissionDefinition.group(MODULE, "capacity", "产能", 50)
                .menu("pmc:capacity:query", "查看")
                .button("pmc:capacity:update", "产能日历");
    }

    @Bean
    public PermissionDefinition pmcShortagePermissions() {
        return PermissionDefinition.group(MODULE, "shortage", "缺料分析", 60)
                .menu("pmc:shortage:query", "查看")
                .button("pmc:shortage:push", "推送催料");
    }

    @Bean
    public PermissionDefinition pmcAlertPermissions() {
        return PermissionDefinition.group(MODULE, "alert", "交期预警", 70)
                .menu("pmc:alert:query", "查看")
                .button("pmc:alert:handle", "处理/忽略");
    }

    @Bean
    public PermissionDefinition pmcShippingPlanPermissions() {
        return PermissionDefinition.group(MODULE, "shipping-plan", "出货计划", 80)
                .menu("pmc:shipping-plan:query", "查看")
                .button("pmc:shipping-plan:create", "新建")
                .button("pmc:shipping-plan:update", "编辑")
                .button("pmc:shipping-plan:publish", "发布/关闭");
    }

    // ==================== 字典 ====================

    @Bean
    public DictDefinition pmcTransportModeDict() {
        return DictDefinition.of("pmc_transport_mode", "运输方式", MODULE)
                .builtin("SEA", "海运", "Sea").builtin("AIR", "空运", "Air").builtin("EXPRESS", "快递", "Express").builtin("LAND", "陆运", "Land");
    }

    @Bean
    public DictDefinition pmcCalendarReasonDict() {
        return DictDefinition.of("pmc_calendar_reason", "产能日历原因", MODULE)
                .builtin("HOLIDAY", "节假日", "Holiday").builtin("OVERTIME", "加班", "Overtime").builtin("MAINTENANCE", "设备保养", "Maintenance")
                .builtin("POWER_OFF", "停电", "Power off").builtin("OTHER", "其他", "Other");
    }

    // ==================== 系统参数 ====================

    @Bean
    public ParamDefinitions pmcParams() {
        return ParamDefinitions.of(
                ParamDefinition.integer(P_HORIZON, MODULE, "MRP", "计划展望期（天）", 180, 7, 730, "只计算需求日期在展望期内的需求").sort(10),
                ParamDefinition.bool(P_USE_MPS, MODULE, "MRP", "成品需求取自 MPS", false, "否：直接用需求池").sort(20),
                ParamDefinition.bool(P_INCLUDE_FORECAST, MODULE, "MRP", "计入净预测", true, "").sort(30),
                ParamDefinition.bool(P_INCLUDE_SAFETY, MODULE, "MRP", "计入安全库存", true, "").sort(40),
                ParamDefinition.bool(P_USE_SUBSTITUTE, MODULE, "MRP", "主料不足时使用替代料库存", false, "").sort(50),
                ParamDefinition.enumOf(P_PO_DATE_BASIS, MODULE, "MRP", "在途采购到货日期依据", "CONFIRMED",
                        List.of(new ParamDefinition.Option("CONFIRMED", "优先确认交期"), new ParamDefinition.Option("REQUIRED", "要求日期")), "").sort(60),
                ParamDefinition.bool(P_NIGHTLY, MODULE, "MRP", "夜间自动全量运算", false, "定时任务 02:30").sort(70),
                ParamDefinition.enumOf(P_LEAD_TIME_BASIS, MODULE, "MRP", "生产提前期依据", "MATERIAL",
                        List.of(new ParamDefinition.Option("MATERIAL", "物料提前期（天）"), new ParamDefinition.Option("ROUTING", "按工艺工时换算")),
                        "按工艺：准备时间 + 数量 × 标准工时，按工作中心日产能换算天数；没有工艺路线时仍取物料提前期").sort(65),
                ParamDefinition.integer(P_TOLERANCE, MODULE, "MRP", "例外信息容差（天）", 3, 0, 60,
                        "供应日期与需求日期相差超过 N 天才产生提前/推迟建议").sort(80),
                ParamDefinition.string(P_ALERT_LEVELS, MODULE, "预警", "交期预警阈值（天）", "2,7", "延期 ≤2 天提示、3～7 天警告、>7 天严重").sort(10),
                ParamDefinition.userList(P_ALERT_MANAGERS, MODULE, "预警", "严重预警通知人（PMC 主管）", "", "为空时通知拥有“交期预警处理”权限的用户").sort(20),
                ParamDefinition.enumOf(P_SCHEDULE_MODE, MODULE, "排产", "排产模式", "INFINITE",
                        List.of(new ParamDefinition.Option("INFINITE", "无限产能（按提前期倒排）"), new ParamDefinition.Option("FINITE", "有限产能（按产能顺排）")),
                        "").sort(10));
    }
}
