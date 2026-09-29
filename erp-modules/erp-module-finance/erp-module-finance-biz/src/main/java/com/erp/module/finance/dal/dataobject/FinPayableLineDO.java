package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 应付单明细（fin_payable_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_payable_line")
public class FinPayableLineDO extends BaseDO {

    private Long payableId;
    private Integer lineNo;
    /** GOODS/RETURN/PROCESS_FEE/ADJUST/PRICE_DIFF/OTHER */
    private String lineType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long statementLineId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private BigDecimal qty;
    private BigDecimal priceInclTax;
    private BigDecimal taxRate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal invoicedQty;
    private BigDecimal invoicedAmount;
}
