package com.erp.module.backup.controller.vo;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 系统备份接口的请求与响应 */
public final class BackupVOs {

    private BackupVOs() {
    }

    /**
     * @param fileStorage   附件存储方式；只有 local 时备份包含附件
     * @param busy          正在进行的操作（备份 / 恢复），空闲为空
     */
    public record BackupInfo(String backupPath, String fileStorage, boolean filesSupported, Map<String, String> schemaVersions, long usableBytes,
                             String busy, boolean maintenance, boolean autoEnabled, int autoKeep, String confirmText) {
    }

    public record BackupReq(Boolean includeFiles, @Size(max = 256) String remark) {
    }

    /**
     * @param restorable 成功且数据库版本与当前系统一致，可以恢复
     * @param mismatch   不能恢复的原因（数据库版本不一致）
     */
    public record RecordRow(Long id, String fileName, Long fileSize, String backupType, String status, boolean includeFiles, Integer tableCount,
                            Long rowCount, Integer attachmentCount, Map<String, String> schemaVersions, String dbProduct, String errorMsg, String remark,
                            LocalDateTime startedAt, LocalDateTime finishedAt, Long operatorId, String operatorName, boolean restorable, String mismatch) {
    }

    /** 恢复前检查 */
    public record RestoreCheck(Long id, String fileName, boolean ok, List<String> problems, Map<String, String> backupVersions,
                               Map<String, String> currentVersions, boolean includeFiles, boolean filesSupported, Long rowCount, Integer attachmentCount,
                               String confirmText) {
    }

    public record RestoreReq(String confirm) {
    }

    public record RestoreLogRow(Long id, Long recordId, String fileName, Long preBackupId, String status, String phase, Integer tableCount, Long rowCount,
                                Integer attachmentCount, String errorMsg, LocalDateTime startedAt, LocalDateTime finishedAt, String operatorName) {
    }
}
