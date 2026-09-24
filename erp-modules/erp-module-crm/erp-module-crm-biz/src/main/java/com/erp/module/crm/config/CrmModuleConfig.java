package com.erp.module.crm.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CrmModuleConfig {

    @Bean
    public ErpModule crmModule() {
        return new ErpModule("crm", "CRM", 200);
    }
}
