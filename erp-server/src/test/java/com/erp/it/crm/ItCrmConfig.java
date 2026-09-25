package com.erp.it.crm;

import com.erp.module.crm.api.credit.CreditUsage;
import com.erp.module.crm.api.credit.CreditUsageProvider;
import com.erp.module.crm.api.customer.CustomerReferenceChecker;
import com.erp.module.crm.api.customer.CustomerOwnerChangedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 测试用的“财务/销售模块”：信用占用、客户业务数据引用 */
@Configuration
class ItCrmConfig {

    static final Map<Long, CreditUsage> USAGE = new ConcurrentHashMap<>();
    static final Set<Long> REFERENCED = ConcurrentHashMap.newKeySet();

    @Bean
    CreditUsageProvider itCreditUsageProvider() {
        return ids -> {
            Map<Long, CreditUsage> map = new HashMap<>();
            ids.forEach(id -> {
                if (USAGE.containsKey(id)) map.put(id, USAGE.get(id));
            });
            return map;
        };
    }

    /** 客户转移事件（销售模块监听） */
    static final List<CustomerOwnerChangedEvent> OWNER_EVENTS = new CopyOnWriteArrayList<>();

    @EventListener
    public void onOwnerChanged(CustomerOwnerChangedEvent e) {
        OWNER_EVENTS.add(e);
    }

    @Bean
    CustomerReferenceChecker itCustomerReferenceChecker() {
        return REFERENCED::contains;
    }
}
