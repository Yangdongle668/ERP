package com.erp.module.backup.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 恢复日志（bak_restore_log） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bak_restore_log")
public class BakRestoreLogDO extends BaseDO {

    private Long recordId;
    private String fileName;
    private Long preBackupId;
    /** RUNNING / SUCCESS / FAILED */
    private String restoreStatus;
    private String phase;
    private Integer tableCount;
    private Long rowCount;
    private Integer attachmentCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMsg;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long operatorId;
}
