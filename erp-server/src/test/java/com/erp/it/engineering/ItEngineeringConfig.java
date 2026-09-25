package com.erp.it.engineering;

import com.erp.module.engineering.api.ecn.EcnImpact;
import com.erp.module.engineering.api.ecn.EcnImpactProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 测试用的“资材/生产模块”：按旧子件返回在途采购、在制生产订单影响 */
@Configuration
class ItEngineeringConfig {

    static final Map<Long, List<EcnImpact>> IMPACTS = new ConcurrentHashMap<>();

    @Bean
    EcnImpactProvider itEcnImpactProvider() {
        return (componentIds, parentIds) -> componentIds.stream().flatMap(id -> IMPACTS.getOrDefault(id, List.of()).stream()).toList();
    }
}
