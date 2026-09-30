package com.erp.module.workbench.config;

import com.erp.framework.module.ErpModule;
import com.erp.module.system.api.param.ParamDefinition;
import com.erp.module.system.api.param.ParamDefinitions;
import com.erp.module.system.api.permission.PermissionDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 工作台模块的声明式注册（需求 02-工作台 README 第 4、5 节） */
@Configuration
public class WorkbenchModuleConfig {

    public static final String MODULE = "workbench";

    public static final String P_TODO_POLL = "wb.todo.poll-seconds";
    public static final String P_MESSAGE_RETENTION = "wb.message.retention-days";
    public static final String P_TODO_RETENTION = "wb.todo.retention-days";
    public static final String P_EMAIL_ENABLED = "wb.email.enabled";
    public static final String P_DASHBOARD_REFRESH = "wb.dashboard.refresh-minutes";

    public static final String PERM_NOTICE = "wb:notice:manage";
    public static final String PERM_ALERT = "wb:alert:handle";

    @Bean
    public ErpModule workbenchModule() {
        return new ErpModule(MODULE, "工作台", 410);
    }

    @Bean
    public PermissionDefinition wbPermissions() {
        return PermissionDefinition.group(MODULE, "workbench", "工作台", 10)
                .menu(PERM_NOTICE, "公告管理")
                .button(PERM_ALERT, "处理预警");
    }

    @Bean
    public ParamDefinitions wbParams() {
        return ParamDefinitions.of(
                ParamDefinition.integer(P_TODO_POLL, MODULE, "待办", "待办角标刷新间隔（秒）", 60, 30, 600, "").sort(10),
                ParamDefinition.integer(P_TODO_RETENTION, MODULE, "待办", "已处理待办保留天数", 365, 30, 3650, "").sort(20),
                ParamDefinition.integer(P_MESSAGE_RETENTION, MODULE, "消息", "消息保留天数", 180, 7, 3650, "").sort(10),
                ParamDefinition.bool(P_EMAIL_ENABLED, MODULE, "邮件", "启用邮件通知", false, "需先配置 SMTP（spring.mail.*）").sort(10),
                ParamDefinition.integer(P_DASHBOARD_REFRESH, MODULE, "看板", "看板数据缓存（分钟）", 15, 0, 1440, "0 表示不缓存").sort(10));
    }
}
