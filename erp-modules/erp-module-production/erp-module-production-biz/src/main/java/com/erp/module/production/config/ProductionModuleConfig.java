package com.erp.module.production.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductionModuleConfig {

    @Bean
    public ErpModule productionModule() {
        return new ErpModule("production", "生产", 240);
    }
}
