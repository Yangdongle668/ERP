package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 进项发票登记（三单匹配）（fin_purchase_invoice） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_purchase_invoice")
public class FinPurchaseInvoiceDO extends BaseDO {

    private String docNo;
    private Long supplierId;
    /** VAT_SPECIAL/VAT_NORMAL/OTHER */
    private String invoiceType;
    private String invoiceNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String invoiceCode;
    private LocalDate invoiceDate;
    private String currency;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    /** UNMATCHED/MATCHED/DIFF */
    private String matchStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long diffConfirmedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime diffConfirmedAt;
    /** NOT_CERTIFIED/CERTIFIED */
    private String deductionStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String certifiedPeriod;
    /** REGISTERED/VOIDED */
    private String invoiceStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String voidReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
