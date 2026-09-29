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

/** 付款单（fin_payment） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "fin_payment", autoResultMap = true)
public class FinPaymentDO extends BaseDocDO {

    private Long requestId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String requestNo;
    private String requestType;
    private Long supplierId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderId;
    private Long bankAccountId;
    private String settlementMethod;
    private LocalDate payDate;
    private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal amount;
    private BigDecimal bankFee;
    private BigDecimal amountBase;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bankRefNo;
    /** 已核销应付（原币） */
    private BigDecimal allocatedAmount;
    /** DRAFT/CONFIRMED/VOIDED */
    private String paymentStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime confirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long voucherId;
}
