package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 销项发票明细（对应应收单行）（fin_sales_invoice_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_sales_invoice_line")
public class FinSalesInvoiceLineDO extends BaseDO {

    private Long invoiceId;
    private Integer lineNo;
    private Long receivableId;
    private Long receivableLineId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderLineId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long materialId;
    private BigDecimal qty;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
}
