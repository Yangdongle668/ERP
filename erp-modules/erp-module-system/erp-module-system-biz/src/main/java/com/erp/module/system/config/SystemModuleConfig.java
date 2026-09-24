package com.erp.module.system.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SystemModuleConfig {

    @Bean
    public ErpModule systemModule() {
        return new ErpModule("system", "系统管理", 10);
    }
}
