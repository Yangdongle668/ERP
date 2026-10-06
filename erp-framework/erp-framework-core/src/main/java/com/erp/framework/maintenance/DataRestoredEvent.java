package com.erp.framework.maintenance;

/**
 * 系统数据整体恢复完成（同步发布，仍在维护模式内）。持有内存缓存或启动时同步数据的组件监听此事件，
 * 清除缓存或重新同步（如权限声明、定时任务）。
 *
 * @param backupFile 恢复所用的备份文件名
 */
public record DataRestoredEvent(String backupFile) {
}
