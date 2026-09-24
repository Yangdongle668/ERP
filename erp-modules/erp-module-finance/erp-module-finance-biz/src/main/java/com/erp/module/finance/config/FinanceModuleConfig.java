package com.erp.module.finance.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FinanceModuleConfig {

    @Bean
    public ErpModule financeModule() {
        return new ErpModule("finance", "财务", 300);
    }
}
