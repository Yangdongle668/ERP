package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 成本核算单（sal_cost_sheet） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_cost_sheet", autoResultMap = true)
public class SalCostSheetDO extends BaseDO {

    private Long rfqId;
    private Long rfqLineId;
    private BigDecimal qty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long bomId;
    private BigDecimal materialCost;
    private BigDecimal laborCost;
    private BigDecimal overheadCost;
    private BigDecimal setupCost;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal toolingTotal;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal toolingQty;
    private BigDecimal toolingCost;
    private BigDecimal packingFreightCost;
    private BigDecimal adminRate;
    private BigDecimal profitRate;
    private BigDecimal totalCost;
    private BigDecimal suggestedPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal suggestedPriceCur;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String detail;
}
