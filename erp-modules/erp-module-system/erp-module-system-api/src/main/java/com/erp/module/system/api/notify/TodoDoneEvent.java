package com.erp.module.system.api.notify;

import com.erp.common.event.DomainEvent;

import java.util.List;

/** 完成或取消待办。userIds 为空表示该 todoKey 的所有处理人。 */
public class TodoDoneEvent extends DomainEvent {

    public enum Result { DONE, CANCELED }

    private final String todoKey;
    private final List<Long> userIds;
    private final Result result;

    public TodoDoneEvent(String todoKey, List<Long> userIds, Result result) {
        this.todoKey = todoKey;
        this.userIds = userIds == null ? List.of() : List.copyOf(userIds);
        this.result = result;
    }

    public String getTodoKey() {
        return todoKey;
    }

    public List<Long> getUserIds() {
        return userIds;
    }

    public Result getResult() {
        return result;
    }
}
