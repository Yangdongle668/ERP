package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 应付单（fin_payable） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "fin_payable", autoResultMap = true)
public class FinPayableDO extends BaseDocDO {

    /** PURCHASE/OUTSOURCE/OTHER */
    private String apType;
    private Long supplierId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long statementId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String statementNo;
    private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountBase;
    private LocalDate bizDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate dueDate;
    private BigDecimal invoicedAmount;
    /** 已申请未付 */
    private BigDecimal requestedAmount;
    /** 已付款核销 */
    private BigDecimal verifiedAmount;
    /** DRAFT/PENDING/CONFIRMED/VOIDED */
    private String apStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime confirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String voidReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long voucherId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
