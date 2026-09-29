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

/** 付款申请（fin_payment_request） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "fin_payment_request", autoResultMap = true)
public class FinPaymentRequestDO extends BaseDocDO {

    private Long supplierId;
    /** PAYABLE/PREPAYMENT */
    private String requestType;
    private String currency;
    private BigDecimal amount;
    private BigDecimal amountBase;
    private LocalDate planPayDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long supplierBankId;
    /** 收款账户快照 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierBankText;
    /** 预付款对应采购订单 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNo;
    private BigDecimal paidAmount;
    /** DRAFT/PENDING/APPROVED/PARTIAL/PAID/CLOSED/VOIDED */
    private String requestStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reason;
    /** 含未收到发票的应付 */
    private Boolean uninvoicedWarning;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime approvedAt;
}
