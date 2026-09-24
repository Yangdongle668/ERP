package com.erp.module.workbench.config;

import com.erp.framework.module.ErpModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkbenchModuleConfig {

    @Bean
    public ErpModule workbenchModule() {
        return new ErpModule("workbench", "工作台", 410);
    }
}
