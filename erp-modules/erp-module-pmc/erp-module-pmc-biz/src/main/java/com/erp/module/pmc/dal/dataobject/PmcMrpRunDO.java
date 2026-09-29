package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** MRP 运算记录（pmc_mrp_run） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_mrp_run")
public class PmcMrpRunDO extends BaseDO {

    private String runNo;
    /** FULL/NET_CHANGE/ORDER */
    private String runType;
    /** 运算范围 JSON */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String scope;
    /** 参数快照 JSON */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String params;
    /** RUNNING/SUCCESS/FAILED */
    private String runStatus;
    private LocalDateTime startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime finishedAt;
    private Integer materialCount;
    private Integer suggestionCount;
    private Integer exceptionCount;
    private Integer progress;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMsg;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long operatorId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long taskId;
}
