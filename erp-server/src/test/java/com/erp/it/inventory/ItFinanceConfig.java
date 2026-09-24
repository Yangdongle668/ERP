package com.erp.it.inventory;

import com.erp.module.inventory.api.period.FinancePeriodChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 测试用的“财务模块”：财务已结账的期间 */
@Configuration
class ItFinanceConfig {

    static final Set<String> CLOSED = ConcurrentHashMap.newKeySet();

    @Bean
    FinancePeriodChecker itFinancePeriodChecker() {
        return CLOSED::contains;
    }
}
