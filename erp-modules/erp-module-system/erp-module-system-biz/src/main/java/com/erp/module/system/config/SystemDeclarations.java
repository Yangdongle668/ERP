package com.erp.module.system.config;

import com.erp.module.system.api.dict.DictDefinition;
import com.erp.module.system.api.param.ParamDefinition;
import com.erp.module.system.api.param.ParamDefinition.Option;
import com.erp.module.system.api.param.ParamDefinitions;
import com.erp.module.system.api.permission.PermissionDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 系统管理模块自身的声明：权限点（与需求文档一致）、内置字典、系统参数（01-10 第 5 节）。
 * 其他模块按同样方式在自己的配置类中声明。
 */
@Configuration
public class SystemDeclarations {

    private static final String M = "system";

    // ==================== 权限点 ====================

    @Bean
    public PermissionDefinition sysOrgPermissions() {
        return PermissionDefinition.group(M, "org", "组织架构", 10)
                .menu("system:org:query", "查看")
                .button("system:org:create", "新建")
                .button("system:org:update", "编辑/启停")
                .button("system:org:delete", "删除");
    }

    @Bean
    public PermissionDefinition sysUserPermissions() {
        return PermissionDefinition.group(M, "user", "用户", 20)
                .menu("system:user:query", "查看")
                .button("system:user:create", "新建")
                .button("system:user:update", "编辑/启停/解锁/下线")
                .button("system:user:reset-password", "重置密码")
                .button("system:user:import", "导入")
                .button("system:user:export", "导出");
    }

    @Bean
    public PermissionDefinition sysRolePermissions() {
        return PermissionDefinition.group(M, "role", "角色", 30)
                .menu("system:role:query", "查看")
                .button("system:role:create", "新建/复制")
                .button("system:role:update", "编辑/启停/成员")
                .button("system:role:grant", "功能权限")
                .button("system:role:delete", "删除");
    }

    @Bean
    public PermissionDefinition sysDictPermissions() {
        return PermissionDefinition.group(M, "dict", "数据字典", 40)
                .menu("system:dict:query", "查看")
                .button("system:dict:create", "新建")
                .button("system:dict:update", "编辑/启停")
                .button("system:dict:delete", "删除");
    }

    @Bean
    public PermissionDefinition sysCodeRulePermissions() {
        return PermissionDefinition.group(M, "code-rule", "编码规则", 50)
                .menu("system:code-rule:query", "查看")
                .button("system:code-rule:update", "修改/调整流水号");
    }

    @Bean
    public PermissionDefinition sysUomPermissions() {
        return PermissionDefinition.group(M, "uom", "计量单位", 60)
                .menu("system:uom:query", "查看")
                .button("system:uom:create", "新建")
                .button("system:uom:update", "编辑/启停/换算")
                .button("system:uom:delete", "删除");
    }

    @Bean
    public PermissionDefinition sysCurrencyPermissions() {
        return PermissionDefinition.group(M, "currency", "币别汇率", 70)
                .menu("system:currency:query", "查看")
                .button("system:currency:create", "新建币别")
                .button("system:currency:update", "编辑/启停币别")
                .button("system:currency:set-base", "设置本位币")
                .button("system:rate:create", "新建汇率")
                .button("system:rate:update", "修改汇率")
                .button("system:rate:delete", "删除汇率")
                .button("system:rate:import", "导入汇率")
                .button("system:rate:export", "导出汇率");
    }

    @Bean
    public PermissionDefinition sysPaymentTermPermissions() {
        return PermissionDefinition.group(M, "payment-term", "付款条件", 80)
                .menu("system:payment-term:query", "查看")
                .button("system:payment-term:create", "新建")
                .button("system:payment-term:update", "编辑/启停")
                .button("system:payment-term:delete", "删除");
    }

    @Bean
    public PermissionDefinition sysWorkflowPermissions() {
        return PermissionDefinition.group(M, "workflow", "审批流", 90)
                .menu("system:workflow:query", "查看")
                .button("system:workflow:update", "配置/发布")
                .button("system:workflow:monitor", "审批监控");
    }

    @Bean
    public PermissionDefinition sysPrintPermissions() {
        return PermissionDefinition.group(M, "print-template", "打印模板", 100)
                .menu("system:print:query", "查看")
                .button("system:print:create", "新建")
                .button("system:print:update", "编辑")
                .button("system:print:delete", "删除");
    }

    @Bean
    public PermissionDefinition sysParamPermissions() {
        return PermissionDefinition.group(M, "param", "系统参数", 110)
                .menu("system:param:query", "查看")
                .button("system:param:update", "修改");
    }

    @Bean
    public PermissionDefinition sysLogPermissions() {
        return PermissionDefinition.group(M, "log", "日志审计", 120)
                .menu("system:log:query", "查看")
                .button("system:log:export", "导出");
    }

    @Bean
    public PermissionDefinition sysJobPermissions() {
        return PermissionDefinition.group(M, "job", "定时任务", 130)
                .menu("system:job:query", "查看")
                .button("system:job:update", "启停/执行")
                .button("system:task:all", "查看所有人的后台任务");
    }

    // ==================== 内置字典 ====================

    @Bean
    public DictDefinition positionDict() {
        return DictDefinition.of("sys_position", "岗位", M);
    }

    @Bean
    public DictDefinition printPaperDict() {
        return DictDefinition.of("sys_print_paper", "打印纸张", M)
                .builtin("A4_P", "A4 纵向", "A4 Portrait")
                .builtin("A4_L", "A4 横向", "A4 Landscape")
                .builtin("A5_L", "A5 横向", "A5 Landscape")
                .builtin("CUSTOM", "自定义", "Custom");
    }

    @Bean
    public DictDefinition tradeTermDict() {
        return DictDefinition.of("sys_trade_term", "贸易条款", M)
                .builtin("EXW", "EXW 工厂交货", "EXW Ex Works")
                .builtin("FCA", "FCA 货交承运人", "FCA Free Carrier")
                .builtin("FOB", "FOB 船上交货", "FOB Free On Board")
                .builtin("CFR", "CFR 成本加运费", "CFR Cost and Freight")
                .builtin("CIF", "CIF 成本、保险费加运费", "CIF Cost, Insurance and Freight")
                .builtin("CPT", "CPT 运费付至", "CPT Carriage Paid To")
                .builtin("CIP", "CIP 运费和保险费付至", "CIP Carriage and Insurance Paid To")
                .builtin("DAP", "DAP 目的地交货", "DAP Delivered At Place")
                .builtin("DPU", "DPU 卸货地交货", "DPU Delivered At Place Unloaded")
                .builtin("DDP", "DDP 完税后交货", "DDP Delivered Duty Paid");
    }

    @Bean
    public DictDefinition settlementMethodDict() {
        return DictDefinition.of("sys_settlement_method", "结算方式", M)
                .builtin("TT", "电汇", "T/T")
                .builtin("LC", "信用证", "L/C")
                .builtin("BANK_ACCEPTANCE", "银行承兑汇票", "Bank Acceptance")
                .builtin("CASH", "现金", "Cash")
                .builtin("CHECK", "支票", "Check")
                .builtin("OTHER", "其他", "Other");
    }

    // ==================== 系统参数（01-10 第 5 节） ====================

    @Bean
    public ParamDefinitions systemParams() {
        String basic = "基本信息";
        String security = "用户与安全";
        return ParamDefinitions.of(
                ParamDefinition.string("sys.company.name", M, basic, "系统名称", "ERP 系统", "显示在登录页和左上角").sort(10),
                ParamDefinition.string("sys.user.init-password", M, security, "用户初始密码", "",
                        "新建和导入用户的默认初始密码；为空时新建用户随机生成，且不允许导入").sort(10),
                ParamDefinition.integer("sys.password.min-length", M, security, "密码最小长度", 8, 8, 32, "新密码的最少位数").sort(20),
                ParamDefinition.enumOf("sys.password.complexity", M, security, "密码复杂度", "LETTER_DIGIT", List.of(
                        new Option("NONE", "不限"),
                        new Option("LETTER_DIGIT", "必须同时包含字母和数字"),
                        new Option("STRONG", "必须包含大写、小写、数字、特殊字符中的三种")), "修改密码时的复杂度要求").sort(30),
                ParamDefinition.integer("sys.password.expire-days", M, security, "密码有效期（天）", 0, 0, 365,
                        "0 表示永不过期；过期后登录强制修改").sort(40),
                ParamDefinition.integer("sys.password.history-count", M, security, "不能与最近几次密码相同", 3, 0, 10,
                        "0 表示不限制").sort(50),
                ParamDefinition.integer("sys.login.max-fail-count", M, security, "登录失败锁定次数", 5, 3, 10,
                        "连续输错密码达到该次数后锁定账号").sort(60),
                ParamDefinition.integer("sys.login.lock-minutes", M, security, "锁定时长（分钟）", 15, 1, 1440, "账号锁定的时长").sort(70),
                ParamDefinition.integer("sys.login.captcha-after-fails", M, security, "失败几次后需要验证码", 3, 0, 10,
                        "0 表示始终需要验证码").sort(80),
                ParamDefinition.integer("sys.session.access-token-minutes", M, security, "访问令牌有效期（分钟）", 120, 15, 1440,
                        "过期后前端自动用刷新令牌续期").sort(90),
                ParamDefinition.integer("sys.session.idle-timeout-minutes", M, security, "无操作自动退出（分钟）", 0, 0, 1440,
                        "0 表示不限制；前端计时，超时后清除令牌跳转登录页").sort(100),
                ParamDefinition.userList("sys.workflow.admin-user-ids", M, "审批", "流程管理员", "1", "接收无审批人节点的任务（用户 ID，逗号分隔）"),
                ParamDefinition.bool("sys.rate.remind-enabled", M, "汇率", "汇率未维护提醒", true, "每个工作日 10:00 检查当天启用外币是否已维护日汇率"),
                ParamDefinition.integer("sys.file.max-size-mb", M, "附件", "单个附件大小上限（MB）", 50, 1, 500, "上传附件的大小上限").sort(10),
                ParamDefinition.string("sys.file.allowed-ext", M, "附件", "允许的附件类型",
                        "jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,zip,rar,7z,dwg,dxf,step,stp", "逗号分隔的扩展名").sort(20),
                ParamDefinition.integer("sys.log.oper-retention-days", M, "日志", "操作日志保留天数", 365, 30, 3650, "超过保留期的操作日志每天 02:00 自动清理").sort(10),
                ParamDefinition.integer("sys.log.login-retention-days", M, "日志", "登录日志保留天数", 365, 30, 3650, "超过保留期的登录日志每天 02:00 自动清理").sort(20),
                ParamDefinition.integer("sys.export.sync-max-rows", M, "导入导出", "同步导出最大行数", 10000, 1000, 50000,
                        "超过时转为后台导出，完成后在任务中心下载"));
    }
}
