package com.erp.module.system.api.task;

/**
 * 后台任务（任务中心，需求 01-系统管理/12 第 2 节）：大批量导出、导入、MRP 运算等耗时操作。
 *
 * <p>任务以提交人的身份（含数据范围）在后台线程执行；同一用户同时运行不超过 3 个，超出排队；
 * 完成时给提交人发送工作台消息。调用方在事务中提交时，任务在事务提交后才开始执行。
 */
public interface AsyncTaskApi {

    String TYPE_EXPORT = "EXPORT";
    String TYPE_IMPORT = "IMPORT";

    /**
     * @param taskType   EXPORT / IMPORT / MRP / COST_CALC …
     * @param name       任务名称，如“导出物料列表”
     * @param moduleCode 所属模块编码
     * @return 任务 ID
     */
    Long submit(String taskType, String name, String moduleCode, TaskRunner runner);
}
