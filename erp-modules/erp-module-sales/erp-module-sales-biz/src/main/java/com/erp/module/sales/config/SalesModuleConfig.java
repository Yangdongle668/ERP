package com.erp.module.sales.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SalesModuleConfig {

    @Bean
    public ErpModule salesModule() {
        return new ErpModule("sales", "销售", 210);
    }
}
