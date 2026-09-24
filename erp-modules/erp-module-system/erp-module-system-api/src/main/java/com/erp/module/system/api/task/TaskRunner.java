package com.erp.module.system.api.task;

/** 后台任务的执行体。抛出异常记为失败（BizException 的提示作为失败原因显示给用户）。 */
@FunctionalInterface
public interface TaskRunner {

    void run(TaskContext context) throws Exception;
}
