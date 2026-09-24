package com.erp.module.system.service.task;

import com.erp.module.system.api.file.FileAccessChecker;
import com.erp.module.system.dal.dataobject.AsyncTaskDO;
import com.erp.module.system.dal.mapper.AsyncTaskMapper;
import org.springframework.stereotype.Component;

/** 后台任务结果文件：提交人和有 system:task:all 权限的用户可下载，不能删除 */
@Component
public class TaskFileAccessChecker implements FileAccessChecker {

    private final AsyncTaskMapper taskMapper;

    public TaskFileAccessChecker(AsyncTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public boolean supports(String bizType) {
        return AsyncTaskService.FILE_BIZ_TYPE.equals(bizType);
    }

    @Override
    public boolean canView(String bizType, Long bizId) {
        AsyncTaskDO t = taskMapper.selectById(bizId);
        return t != null && AsyncTaskService.canView(t);
    }

    @Override
    public boolean canEdit(String bizType, Long bizId) {
        return false;
    }
}
