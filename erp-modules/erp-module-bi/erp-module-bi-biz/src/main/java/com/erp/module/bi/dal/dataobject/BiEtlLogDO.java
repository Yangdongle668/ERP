package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 数据任务日志（bi_etl_log） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_etl_log")
public class BiEtlLogDO extends BaseDO {

    private String jobCode;
    private LocalDateTime startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime finishedAt;
    private String result;
    private Integer rowCount;
    private Integer diffRows;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String message;
}
