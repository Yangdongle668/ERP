package com.erp.module.inventory.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventoryModuleConfig {

    @Bean
    public ErpModule inventoryModule() {
        return new ErpModule("inventory", "仓库", 120);
    }
}
