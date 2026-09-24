package com.erp.module.pmc.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PmcModuleConfig {

    @Bean
    public ErpModule pmcModule() {
        return new ErpModule("pmc", "PMC", 230);
    }
}
