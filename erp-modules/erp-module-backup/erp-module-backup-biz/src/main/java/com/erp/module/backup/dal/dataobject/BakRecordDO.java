package com.erp.module.backup.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 备份记录（bak_record） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bak_record")
public class BakRecordDO extends BaseDO {

    private String fileName;
    private Long fileSize;
    private String sha256;
    /** MANUAL / AUTO / PRE_RESTORE / UPLOAD */
    private String backupType;
    /** RUNNING / SUCCESS / FAILED */
    private String recordStatus;
    private Boolean includeFiles;
    private Integer tableCount;
    private Long rowCount;
    private Integer attachmentCount;
    private String schemaVersions;
    private String dbProduct;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMsg;
    private String remark;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long operatorId;
}
