package com.erp.module.quality.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QualityModuleConfig {

    @Bean
    public ErpModule qualityModule() {
        return new ErpModule("quality", "品质", 250);
    }
}
