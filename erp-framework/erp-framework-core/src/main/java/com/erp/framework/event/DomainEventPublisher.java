package com.erp.framework.event;

import com.erp.common.event.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 领域事件发布器。当前基于 Spring 进程内事件；
 * 将来拆分微服务时只需替换本类实现（如改为发 MQ），业务代码不变。
 */
@Slf4j
@Component
public class DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    public DomainEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    public void publish(DomainEvent event) {
        log.debug("[领域事件] {} id={}", event.getClass().getSimpleName(), event.getEventId());
        delegate.publishEvent(event);
    }
}
