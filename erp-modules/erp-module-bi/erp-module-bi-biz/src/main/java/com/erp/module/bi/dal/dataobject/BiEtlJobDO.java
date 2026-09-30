package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 数据任务（bi_etl_job） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_etl_job")
public class BiEtlJobDO extends BaseDO {

    private String code;
    private String name;
    /** IDLE/RUNNING */
    private String jobStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lastStartedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lastFinishedAt;
    /** SUCCESS/FAILED */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastResult;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer lastRows;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer lastDiffRows;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long lastDurationMs;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastMessage;
}
