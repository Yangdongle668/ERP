package com.erp.module.purchase.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PurchaseModuleConfig {

    @Bean
    public ErpModule purchaseModule() {
        return new ErpModule("purchase", "资材", 220);
    }
}
