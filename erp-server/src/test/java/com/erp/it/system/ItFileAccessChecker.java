package com.erp.it.system;

import com.erp.module.system.api.file.FileAccessChecker;
import org.springframework.stereotype.Component;

/** 测试用：业务类型 IT_LOCKED 的附件任何人都不能查看、编辑（验证 SYS-FIL-R04 扩展点） */
@Component
class ItFileAccessChecker implements FileAccessChecker {

    static final String LOCKED = "IT_LOCKED";

    @Override
    public boolean supports(String bizType) {
        return LOCKED.equals(bizType);
    }

    @Override
    public boolean canView(String bizType, Long bizId) {
        return false;
    }

    @Override
    public boolean canEdit(String bizType, Long bizId) {
        return false;
    }
}
