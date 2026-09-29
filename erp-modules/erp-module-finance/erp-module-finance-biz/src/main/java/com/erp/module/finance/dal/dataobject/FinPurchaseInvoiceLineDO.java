package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 进项发票匹配明细（fin_purchase_invoice_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_purchase_invoice_line")
public class FinPurchaseInvoiceLineDO extends BaseDO {

    private Long invoiceId;
    private Integer lineNo;
    private Long payableId;
    private Long payableLineId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long materialId;
    private BigDecimal qty;
    /** 发票不含税单价 */
    private BigDecimal invoicePrice;
    /** 应付不含税单价 */
    private BigDecimal apPrice;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    /** 冲减应付行未开票的金额（价税合计） */
    private BigDecimal apAmount;
    private BigDecimal priceDiffPct;
    private Boolean overTolerance;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String diffReason;
}
