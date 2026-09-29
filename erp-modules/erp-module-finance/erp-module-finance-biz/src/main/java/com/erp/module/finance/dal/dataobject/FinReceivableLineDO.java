package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 应收单明细（fin_receivable_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_receivable_line")
public class FinReceivableLineDO extends BaseDO {

    private Long receivableId;
    private Integer lineNo;
    private String arType;
    /** 出货单行 / 退货单行 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceLineId;
    /** 有效时 = source_line_id，作废后置空（幂等唯一键） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceLineKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderLineId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    /** 基本单位 */
    private BigDecimal qty;
    private BigDecimal priceInclTax;
    private BigDecimal taxRate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal invoicedQty;
    private BigDecimal invoicedAmount;
}
