package com.erp.module.system.api.user;

import com.erp.common.event.DomainEvent;

/** 用户被停用（停用事务提交后由监听方处理；工作台据此把任务类待办转给部门负责人，需求 02-02 WB-TODO-R03） */
public class UserDeactivatedEvent extends DomainEvent {

    private final Long userId;

    public UserDeactivatedEvent(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
