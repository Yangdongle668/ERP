package com.erp.module.system.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 系统管理模块配置。定时任务统一用 @ErpJob 声明（见 SystemJobs），不使用 @Scheduled。 */
@Configuration
public class SystemModuleConfig {

    @Bean
    public ErpModule systemModule() {
        return new ErpModule("system", "系统管理", 10);
    }
}
