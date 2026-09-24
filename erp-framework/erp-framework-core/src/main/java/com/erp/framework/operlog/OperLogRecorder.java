package com.erp.framework.operlog;

/**
 * 扩展点：保存操作日志，由系统管理模块实现。框架在独立线程池中调用，实现方直接写库即可；
 * 抛出的异常只记录到应用日志，不影响业务请求。
 */
public interface OperLogRecorder {

    void record(OperLogRecord record);
}
