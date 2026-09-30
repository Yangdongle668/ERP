package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 成本计算异常（fin_cost_exception） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_cost_exception")
public class FinCostExceptionDO extends BaseDO {

    private String period;
    private Long runId;
    /** NEGATIVE_BALANCE/PRICE_SWING/ZERO_COST_OUT/NO_HOURS/NO_PRICE_IN/CYCLE */
    private String exType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long prodOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deptId;
    private String message;
}
