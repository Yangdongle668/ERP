package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 销项发票登记（fin_sales_invoice） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_sales_invoice")
public class FinSalesInvoiceDO extends BaseDO {

    private String docNo;
    private Long customerId;
    /** VAT_SPECIAL/VAT_NORMAL/EXPORT/OTHER */
    private String invoiceType;
    private String invoiceNo;
    private LocalDate invoiceDate;
    private String currency;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    /** REGISTERED/VOIDED/RED */
    private String invoiceStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String voidReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
