package com.erp.module.system.api.notify;

import com.erp.common.event.DomainEvent;

import java.util.List;

/** 发送站内消息（可同时发邮件）。 */
public class MessageSendEvent extends DomainEvent {

    public enum Type { NOTICE, APPROVAL_RESULT, TASK_DONE, REMIND, SYSTEM }

    private final List<Long> userIds;
    private final Type msgType;
    private final String title;
    private final String content;
    private final String route;
    private final boolean sendEmail;

    public MessageSendEvent(List<Long> userIds, Type msgType, String title, String content, String route, boolean sendEmail) {
        this.userIds = List.copyOf(userIds);
        this.msgType = msgType;
        this.title = title;
        this.content = content;
        this.route = route;
        this.sendEmail = sendEmail;
    }

    public List<Long> getUserIds() {
        return userIds;
    }

    public Type getMsgType() {
        return msgType;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getRoute() {
        return route;
    }

    public boolean isSendEmail() {
        return sendEmail;
    }
}
