package com.erp.module.system.service.support;

/** 系统管理模块内部事件：用户被停用（在停用事务内同步发布；审批流据此转交其待办，需求 08 R12） */
public record UserDisabledEvent(Long userId) {
}
