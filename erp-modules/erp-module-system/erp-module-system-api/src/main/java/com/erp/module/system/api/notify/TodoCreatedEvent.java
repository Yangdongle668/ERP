package com.erp.module.system.api.notify;

import com.erp.common.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

/** 创建待办（需求 02-工作台/README 第 1.1 节）。同一 todoKey + 用户重复发布时更新而不新增。 */
public class TodoCreatedEvent extends DomainEvent {

    public enum Category { APPROVAL, TASK }

    public enum Priority { HIGH, NORMAL, LOW }

    private final String todoKey;
    private final List<Long> userIds;
    private final Category category;
    private final String bizType;
    private final Long bizId;
    private final String bizNo;
    private final String title;
    private final String route;
    private final Priority priority;
    private final LocalDateTime dueTime;

    public TodoCreatedEvent(String todoKey, List<Long> userIds, Category category, String bizType, Long bizId,
                            String bizNo, String title, String route, Priority priority, LocalDateTime dueTime) {
        this.todoKey = todoKey;
        this.userIds = List.copyOf(userIds);
        this.category = category;
        this.bizType = bizType;
        this.bizId = bizId;
        this.bizNo = bizNo;
        this.title = title;
        this.route = route;
        this.priority = priority == null ? Priority.NORMAL : priority;
        this.dueTime = dueTime;
    }

    public String getTodoKey() {
        return todoKey;
    }

    public List<Long> getUserIds() {
        return userIds;
    }

    public Category getCategory() {
        return category;
    }

    public String getBizType() {
        return bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public String getBizNo() {
        return bizNo;
    }

    public String getTitle() {
        return title;
    }

    public String getRoute() {
        return route;
    }

    public Priority getPriority() {
        return priority;
    }

    public LocalDateTime getDueTime() {
        return dueTime;
    }
}
