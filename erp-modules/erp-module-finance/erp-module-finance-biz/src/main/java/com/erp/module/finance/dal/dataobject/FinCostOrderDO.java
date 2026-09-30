package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 生产订单成本（fin_cost_order） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_cost_order")
public class FinCostOrderDO extends BaseDO {

    private String period;
    private Long prodOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String prodOrderNo;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deptId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderType;
    private BigDecimal workHours;
    private BigDecimal openingWip;
    private BigDecimal openingWipMaterial;
    private BigDecimal materialCost;
    private BigDecimal laborCost;
    private BigDecimal overheadCost;
    private BigDecimal totalCost;
    private BigDecimal finishedQty;
    private BigDecimal finishedCost;
    private BigDecimal finishedMaterial;
    private BigDecimal finishedLabor;
    private BigDecimal finishedOverhead;
    private BigDecimal endingWip;
    private BigDecimal endingWipMaterial;
    /** 累计完工 */
    private BigDecimal cumFinishedQty;
    private BigDecimal unitCost;
}
