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
    public static final String P_WEATHER_ENABLED = "wb.weather.enabled";
    public static final String P_WEATHER_CITY = "wb.weather.city";
    public static final String P_WEATHER_LAT = "wb.weather.latitude";
    public static final String P_WEATHER_LON = "wb.weather.longitude";

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
                ParamDefinition.integer(P_DASHBOARD_REFRESH, MODULE, "看板", "看板数据缓存（分钟）", 15, 0, 1440, "0 表示不缓存").sort(10),
                ParamDefinition.bool(P_WEATHER_ENABLED, MODULE, "天气", "首页显示天气预报", true, "数据来自 Open-Meteo（免费，无需密钥），服务器需能访问 api.open-meteo.com").sort(10),
                ParamDefinition.string(P_WEATHER_CITY, MODULE, "天气", "天气城市名称", "深圳宝安", "只用于显示").sort(20),
                ParamDefinition.decimal(P_WEATHER_LAT, MODULE, "天气", "纬度", "22.5550", "-90", "90", "如深圳宝安 22.5550").sort(30),
                ParamDefinition.decimal(P_WEATHER_LON, MODULE, "天气", "经度", "113.8830", "-180", "180", "如深圳宝安 113.8830").sort(40));
    }
}
