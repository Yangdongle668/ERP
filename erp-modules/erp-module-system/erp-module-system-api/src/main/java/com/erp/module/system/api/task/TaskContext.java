package com.erp.module.system.api.task;

/** 后台任务执行上下文：汇报进度、保存结果文件与说明。 */
public interface TaskContext {

    Long taskId();

    /** 进度 0～100（写库有节流，可频繁调用） */
    void progress(int percent);

    /** 保存结果文件（导出文件、导入错误报告），用户在任务中心下载；保留 7 天 */
    void resultFile(String fileName, String contentType, byte[] content);

    /** 结果说明，如“成功导出 25,318 行” */
    void resultMessage(String message);
}
