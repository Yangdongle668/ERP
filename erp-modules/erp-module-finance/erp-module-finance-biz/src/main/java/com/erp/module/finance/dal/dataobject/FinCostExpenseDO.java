package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 本期费用（按车间）（fin_cost_expense） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_cost_expense")
public class FinCostExpenseDO extends BaseDO {

    private String period;
    /** 车间 */
    private Long deptId;
    private BigDecimal laborAmount;
    private BigDecimal overheadAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
