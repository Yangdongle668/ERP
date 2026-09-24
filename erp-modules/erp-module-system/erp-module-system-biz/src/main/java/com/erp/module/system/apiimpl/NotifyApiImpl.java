package com.erp.module.system.apiimpl;

import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.notify.AlertResolvedEvent;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.notify.TodoCreatedEvent;
import com.erp.module.system.api.notify.TodoDoneEvent;
import org.springframework.stereotype.Service;

/** 只负责发布平台事件；存储与展示由工作台模块监听完成。 */
@Service
public class NotifyApiImpl implements NotifyApi {

    private final DomainEventPublisher publisher;

    public NotifyApiImpl(DomainEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void todo(TodoCreatedEvent event) {
        publisher.publish(event);
    }

    @Override
    public void done(TodoDoneEvent event) {
        publisher.publish(event);
    }

    @Override
    public void message(MessageSendEvent event) {
        publisher.publish(event);
    }

    @Override
    public void alert(AlertRaisedEvent event) {
        publisher.publish(event);
    }

    @Override
    public void resolve(String alertKey) {
        publisher.publish(new AlertResolvedEvent(alertKey));
    }
}
