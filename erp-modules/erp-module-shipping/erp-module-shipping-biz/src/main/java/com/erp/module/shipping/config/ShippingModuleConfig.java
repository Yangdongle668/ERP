package com.erp.module.shipping.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShippingModuleConfig {

    @Bean
    public ErpModule shippingModule() {
        return new ErpModule("shipping", "出货", 260);
    }
}
