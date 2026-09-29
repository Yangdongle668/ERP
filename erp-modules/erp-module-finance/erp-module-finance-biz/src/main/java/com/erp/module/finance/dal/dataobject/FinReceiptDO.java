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

/** 收款单（fin_receipt） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "fin_receipt", autoResultMap = true)
public class FinReceiptDO extends BaseDocDO {

    private Long customerId;
    /** SALES/ADVANCE/OTHER/REFUND */
    private String receiptType;
    private Long bankAccountId;
    private String settlementMethod;
    private LocalDate receiptDate;
    private String currency;
    private BigDecimal exchangeRate;
    /** 到账金额（退款为负） */
    private BigDecimal amount;
    private BigDecimal bankFee;
    private BigDecimal amountBase;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bankRefNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String payerName;
    /** 预收款对应销售订单 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNo;
    /** 已核销（原币，含手续费部分） */
    private BigDecimal allocatedAmount;
    /** DRAFT/CONFIRMED/VOIDED */
    private String receiptStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime confirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long voucherId;
}
