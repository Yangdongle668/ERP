package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 成本计算记录（fin_cost_run） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_cost_run")
public class FinCostRunDO extends BaseDO {

    private String period;
    /** RUNNING/SUCCESS/FAILED */
    private String runStatus;
    private LocalDateTime startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime finishedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long operatorId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;
    private Integer materialCount;
    private Integer orderCount;
    /** 完工产品总成本 */
    private BigDecimal totalCost;
    private Integer exceptionCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesCostVoucherId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long issueVoucherId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long finishVoucherId;
}
