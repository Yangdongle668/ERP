package com.erp.module.system.api.notify;

import com.erp.common.event.DomainEvent;

import java.util.List;

/**
 * 发出预警。同一 alertKey 未处理时更新内容而不是新增。
 * 接收人：userIds 非空时使用 userIds，否则发给拥有 permission 的全部用户。
 */
public class AlertRaisedEvent extends DomainEvent {

    public enum Level { INFO, WARNING, CRITICAL }

    private final String alertKey;
    private final String alertType;
    private final Level level;
    private final List<Long> userIds;
    private final String permission;
    private final String bizType;
    private final Long bizId;
    private final String title;
    private final String content;
    private final String route;

    public AlertRaisedEvent(String alertKey, String alertType, Level level, List<Long> userIds, String permission,
                            String bizType, Long bizId, String title, String content, String route) {
        this.alertKey = alertKey;
        this.alertType = alertType;
        this.level = level;
        this.userIds = userIds == null ? List.of() : List.copyOf(userIds);
        this.permission = permission;
        this.bizType = bizType;
        this.bizId = bizId;
        this.title = title;
        this.content = content;
        this.route = route;
    }

    public String getAlertKey() {
        return alertKey;
    }

    public String getAlertType() {
        return alertType;
    }

    public Level getLevel() {
        return level;
    }

    public List<Long> getUserIds() {
        return userIds;
    }

    public String getPermission() {
        return permission;
    }

    public String getBizType() {
        return bizType;
    }

    public Long getBizId() {
        return bizId;
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
}
