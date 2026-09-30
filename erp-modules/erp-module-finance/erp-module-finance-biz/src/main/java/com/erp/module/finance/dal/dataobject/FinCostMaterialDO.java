package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 物料加权结果（fin_cost_material） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_cost_material")
public class FinCostMaterialDO extends BaseDO {

    private String period;
    private Long materialId;
    private BigDecimal openingQty;
    private BigDecimal openingAmount;
    /** 参与加权的本期入库 */
    private BigDecimal inQty;
    private BigDecimal inAmount;
    /** 加权单价 */
    private BigDecimal unitCost;
    /** 上期单价 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal prevUnitCost;
    /** 按加权单价计价的净出库 */
    private BigDecimal outQty;
    private BigDecimal outAmount;
    private BigDecimal closingQty;
    private BigDecimal closingAmount;
    /** 销售净出库（扣退货） */
    private BigDecimal salesOutQty;
    private BigDecimal salesOutAmount;
    /** 生产领料净额（扣退料） */
    private BigDecimal issueAmount;
    /** 生产入库金额 */
    private BigDecimal productionInAmount;
}
