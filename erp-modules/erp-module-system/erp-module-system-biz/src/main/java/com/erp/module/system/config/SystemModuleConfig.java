package com.erp.module.system.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 系统管理模块配置。启用定时任务（日志清理等；定时任务管理页面上线前使用 @Scheduled）。 */
@Configuration
@EnableScheduling
public class SystemModuleConfig {

    @Bean
    public ErpModule systemModule() {
        return new ErpModule("system", "系统管理", 10);
    }
}
