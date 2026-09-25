package com.erp.it.sales;

import com.erp.module.sales.api.cost.SalesCostProvider;
import com.erp.module.sales.api.order.SalesOrderApprovedEvent;
import com.erp.module.sales.api.order.SalesOrderChangedEvent;
import com.erp.module.sales.api.order.SalesOrderClosedEvent;
import com.erp.module.sales.api.order.SalesOrderReferenceChecker;
import com.erp.module.sales.api.returns.SalesReturnReceivedEvent;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.TodoCreatedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 测试用的“研发工程 / 财务 / PMC / 生产模块”：标准成本、订单引用，以及销售事件的接收方 */
@Configuration
class ItSalesConfig {

    /** 物料标准成本（本位币 / 基本单位） */
    static final Map<Long, BigDecimal> COSTS = new ConcurrentHashMap<>();
    /** 被生产订单引用的销售订单 */
    static final Set<Long> REFERENCED = ConcurrentHashMap.newKeySet();

    static final List<SalesOrderApprovedEvent> APPROVED = new CopyOnWriteArrayList<>();
    static final List<SalesOrderChangedEvent> CHANGED = new CopyOnWriteArrayList<>();
    static final List<SalesOrderClosedEvent> CLOSED = new CopyOnWriteArrayList<>();
    static final List<SalesReturnReceivedEvent> RETURNS = new CopyOnWriteArrayList<>();
    static final List<MessageSendEvent> MESSAGES = new CopyOnWriteArrayList<>();
    static final List<TodoCreatedEvent> TODOS = new CopyOnWriteArrayList<>();

    @Bean
    SalesCostProvider itSalesCostProvider() {
        return ids -> {
            Map<Long, BigDecimal> map = new HashMap<>();
            ids.forEach(id -> {
                if (COSTS.containsKey(id)) map.put(id, COSTS.get(id));
            });
            return map;
        };
    }

    @Bean
    SalesOrderReferenceChecker itSalesOrderReferenceChecker() {
        return orderId -> REFERENCED.contains(orderId) ? Optional.of("生产订单 MO-TEST") : Optional.empty();
    }

    @EventListener
    public void onApproved(SalesOrderApprovedEvent e) {
        APPROVED.add(e);
    }

    @EventListener
    public void onChanged(SalesOrderChangedEvent e) {
        CHANGED.add(e);
    }

    @EventListener
    public void onClosed(SalesOrderClosedEvent e) {
        CLOSED.add(e);
    }

    @EventListener
    public void onMessage(MessageSendEvent e) {
        MESSAGES.add(e);
    }

    @EventListener
    public void onTodo(TodoCreatedEvent e) {
        TODOS.add(e);
    }

    @EventListener
    public void onReturn(SalesReturnReceivedEvent e) {
        RETURNS.add(e);
    }
}
