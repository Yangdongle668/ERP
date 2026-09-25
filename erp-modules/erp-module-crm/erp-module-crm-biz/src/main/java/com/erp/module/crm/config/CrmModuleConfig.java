package com.erp.module.crm.config;

import com.erp.framework.module.ErpModule;
import com.erp.module.system.api.coderule.CodeRuleDefinition;
import com.erp.module.system.api.coderule.CodeRuleDefinition.ResetCycle;
import com.erp.module.system.api.dict.DictDefinition;
import com.erp.module.system.api.dict.DictDefinition.Item;
import com.erp.module.system.api.dict.DictDefinition.TagType;
import com.erp.module.system.api.param.ParamDefinition;
import com.erp.module.system.api.param.ParamDefinitions;
import com.erp.module.system.api.permission.PermissionDefinition;
import com.erp.module.system.api.workflow.ApprovalBizDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** CRM 模块的声明式注册（需求 03-CRM README 第 5～8、10 节） */
@Configuration
public class CrmModuleConfig {

    public static final String MODULE = "crm";

    // 编码规则 / 单据类型（审批、附件、操作日志共用）
    public static final String CUSTOMER = "CRM_CUSTOMER";
    public static final String CUSTOMER_ACTIVATE = "CRM_CUSTOMER_ACTIVATE";
    public static final String CREDIT_CHANGE = "CRM_CREDIT_CHANGE";
    public static final String OPPORTUNITY = "CRM_OPPORTUNITY";
    public static final String FOLLOWUP = "CRM_FOLLOWUP";
    public static final String CUSTOMER_PART = "CRM_CUSTOMER_PART";

    // 系统参数
    public static final String P_DUPLICATE_CHECK = "crm.customer.duplicate-check";
    public static final String P_CREDIT_MODE = "crm.credit.control-mode";
    public static final String P_CREDIT_POINTS = "crm.credit.check-points";
    public static final String P_FOLLOWUP_REMIND = "crm.followup.remind-time";

    /** 字段权限：在客户页面查看、设置信用字段 */
    public static final String CREDIT_FIELD = "crm:customer:credit";

    @Bean
    public ErpModule crmModule() {
        return new ErpModule(MODULE, "CRM", 200);
    }

    // ==================== 编码规则 ====================

    @Bean
    public CodeRuleDefinition crmCustomerCodeRule() {
        return CodeRuleDefinition.of(CUSTOMER, "客户编码", MODULE, "C", "", "", 5, ResetCycle.NEVER).manual(true);
    }

    @Bean
    public CodeRuleDefinition crmOpportunityCodeRule() {
        return CodeRuleDefinition.of(OPPORTUNITY, "商机", MODULE, "OPP-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    // ==================== 权限点 ====================

    @Bean
    public PermissionDefinition crmCustomerPermissions() {
        return PermissionDefinition.group(MODULE, "customer", "客户", 10)
                .menu("crm:customer:query", "查看")
                .button("crm:customer:create", "新建")
                .button("crm:customer:update", "编辑")
                .button("crm:customer:activate", "转正式")
                .button("crm:customer:disable", "停用/启用")
                .button("crm:customer:blacklist", "黑名单")
                .button("crm:customer:transfer", "转移")
                .button("crm:customer:delete", "删除")
                .button("crm:customer:import", "导入")
                .button("crm:customer:export", "导出")
                .field(CREDIT_FIELD, "查看信用字段");
    }

    @Bean
    public PermissionDefinition crmCustomerPartPermissions() {
        return PermissionDefinition.group(MODULE, "customer-part", "客户料号", 20)
                .menu("crm:customer-part:query", "查看")
                .button("crm:customer-part:create", "新建")
                .button("crm:customer-part:update", "编辑/停用")
                .button("crm:customer-part:delete", "删除")
                .button("crm:customer-part:import", "导入");
    }

    @Bean
    public PermissionDefinition crmCreditPermissions() {
        return PermissionDefinition.group(MODULE, "credit", "客户信用", 30)
                .menu("crm:credit:query", "查看")
                .button("crm:credit:update", "调整额度");
    }

    @Bean
    public PermissionDefinition crmFollowupPermissions() {
        return PermissionDefinition.group(MODULE, "followup", "跟进记录", 40)
                .menu("crm:followup:query", "查看")
                .button("crm:followup:create", "新增")
                .button("crm:followup:update", "编辑")
                .button("crm:followup:delete", "删除");
    }

    @Bean
    public PermissionDefinition crmOpportunityPermissions() {
        return PermissionDefinition.group(MODULE, "opportunity", "商机", 50)
                .menu("crm:opportunity:query", "查看")
                .button("crm:opportunity:create", "新建")
                .button("crm:opportunity:update", "编辑/推进阶段")
                .button("crm:opportunity:delete", "删除")
                .button("crm:opportunity:close", "赢单/输单/搁置");
    }

    // ==================== 字典 ====================

    @Bean
    public DictDefinition crmCustomerTypeDict() {
        return DictDefinition.of("crm_customer_type", "客户类型", MODULE)
                .builtin("END_USER", "终端客户", "End user").builtin("TRADER", "贸易商", "Trader")
                .builtin("AGENT", "代理商", "Agent").builtin("OEM", "品牌商", "OEM");
    }

    @Bean
    public DictDefinition crmCustomerLevelDict() {
        return DictDefinition.of("crm_customer_level", "客户等级", MODULE)
                .add(new Item("A", "A 级重点客户", "A", TagType.SUCCESS, true, false))
                .add(new Item("B", "B 级", "B", TagType.PRIMARY, true, false))
                .add(new Item("C", "C 级", "C", TagType.WARNING, true, true))
                .add(new Item("D", "D 级风险客户", "D", TagType.DANGER, true, false));
    }

    @Bean
    public DictDefinition crmIndustryDict() {
        return DictDefinition.of("crm_industry", "行业", MODULE);
    }

    @Bean
    public DictDefinition crmSourceDict() {
        return DictDefinition.of("crm_source", "客户来源", MODULE)
                .builtin("EXHIBITION", "展会", "Exhibition").builtin("WEBSITE", "官网", "Website").builtin("B2B", "B2B 平台", "B2B")
                .builtin("REFERRAL", "转介绍", "Referral").builtin("COLD_CALL", "陌生开发", "Cold call").builtin("OTHER", "其他", "Other");
    }

    @Bean
    public DictDefinition crmContactRoleDict() {
        return DictDefinition.of("crm_contact_role", "联系人角色", MODULE)
                .builtin("PURCHASE", "采购", "Purchase").builtin("ENGINEERING", "工程", "Engineering").builtin("QUALITY", "品质", "Quality")
                .builtin("FINANCE", "财务", "Finance").builtin("LOGISTICS", "物流", "Logistics").builtin("MANAGEMENT", "管理层", "Management")
                .builtin("OTHER", "其他", "Other");
    }

    @Bean
    public DictDefinition crmFollowupTypeDict() {
        return DictDefinition.of("crm_followup_type", "跟进方式", MODULE)
                .builtin("VISIT", "拜访", "Visit").builtin("PHONE", "电话", "Phone").builtin("EMAIL", "邮件", "Email")
                .builtin("EXHIBITION", "展会", "Exhibition").builtin("VIDEO", "视频会议", "Video").builtin("OTHER", "其他", "Other");
    }

    @Bean
    public DictDefinition crmLostReasonDict() {
        return DictDefinition.of("crm_lost_reason", "输单原因", MODULE)
                .builtin("PRICE", "价格", "Price").builtin("DELIVERY", "交期", "Delivery").builtin("QUALITY", "质量", "Quality")
                .builtin("COMPETITOR", "竞争对手", "Competitor").builtin("PROJECT_CANCELED", "项目取消", "Project canceled")
                .builtin("OTHER", "其他", "Other");
    }

    // ==================== 系统参数 ====================

    @Bean
    public ParamDefinitions crmParams() {
        return ParamDefinitions.of(
                ParamDefinition.enumOf(P_DUPLICATE_CHECK, MODULE, "客户", "客户查重方式", "WARN",
                        List.of(new ParamDefinition.Option("OFF", "不检查"), new ParamDefinition.Option("WARN", "提示"),
                                new ParamDefinition.Option("BLOCK", "阻止保存")), "名称（忽略大小写、空格、公司后缀）、税号、网址域名相同视为疑似重复").sort(10),
                ParamDefinition.enumOf(P_CREDIT_MODE, MODULE, "信用", "信用控制方式", "WARN",
                        List.of(new ParamDefinition.Option("NONE", "不控制"), new ParamDefinition.Option("WARN", "警告"),
                                new ParamDefinition.Option("BLOCK", "阻止")), "客户未单独设置时使用").sort(10),
                ParamDefinition.string(P_CREDIT_POINTS, MODULE, "信用", "信用检查时点", "ORDER,SHIPMENT",
                        "逗号分隔：ORDER 销售订单审核、SHIPMENT 出货单提交").sort(20),
                ParamDefinition.time(P_FOLLOWUP_REMIND, MODULE, "跟进", "跟进提醒时间", "09:00", "每天该时间提醒当天到期的跟进").sort(10));
    }

    // ==================== 审批 ====================

    @Bean
    public ApprovalBizDefinition crmCustomerActivateApproval() {
        return ApprovalBizDefinition.of(CUSTOMER_ACTIVATE, "客户转正式", MODULE, "/crm/customer/{id}")
                .dictField("customerType", "客户类型", "crm_customer_type")
                .stringField("country", "国家")
                .numberField("creditLimitBase", "信用额度(本位币)")
                .userField("ownerId", "负责业务员");
    }

    @Bean
    public ApprovalBizDefinition crmCreditChangeApproval() {
        return ApprovalBizDefinition.of(CREDIT_CHANGE, "信用额度调整", MODULE, "/crm/credit?change={id}")
                .numberField("newLimitBase", "新额度(本位币)")
                .numberField("increaseBase", "增加额(本位币)")
                .userField("ownerId", "负责业务员");
    }
}
