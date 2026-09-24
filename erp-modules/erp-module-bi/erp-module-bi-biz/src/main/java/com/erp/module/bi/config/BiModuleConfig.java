package com.erp.module.bi.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BiModuleConfig {

    @Bean
    public ErpModule biModule() {
        return new ErpModule("bi", "BI/AI", 400);
    }
}
