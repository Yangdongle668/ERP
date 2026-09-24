package com.erp.module.system.api.notify;

/**
 * 待办、消息、预警的发送入口。实现只发布对应的平台事件，由工作台模块监听并存储。
 * 业务模块使用本接口即可，不需要依赖工作台模块。
 */
public interface NotifyApi {

    void todo(TodoCreatedEvent event);

    void done(TodoDoneEvent event);

    void message(MessageSendEvent event);

    void alert(AlertRaisedEvent event);

    void resolve(String alertKey);
}
