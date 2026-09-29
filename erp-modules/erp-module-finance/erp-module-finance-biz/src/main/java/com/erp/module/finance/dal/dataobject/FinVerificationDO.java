package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 核销记录（收款 / 付款共用）（fin_verification） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_verification")
public class FinVerificationDO extends BaseDO {

    /** RECEIPT_AR/ADVANCE_AR/RED_BLUE_AR/PAYMENT_AP/PREPAY_AP/RED_BLUE_AP */
    private String verifyType;
    /** 同一次核销操作 */
    private String batchNo;
    /** CUSTOMER/SUPPLIER */
    private String partnerType;
    private Long partnerId;
    private String currency;
    /** RECEIPT/PAYMENT/RECEIVABLE/PAYABLE */
    private String docAType;
    private Long docAId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String docANo;
    private String docBType;
    private Long docBId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String docBNo;
    /** 应收关联订单（回款计划） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderId;
    private BigDecimal amount;
    private BigDecimal amountBaseA;
    private BigDecimal amountBaseB;
    /** 汇兑差异 = base_a − base_b（正为收益） */
    private BigDecimal fxDiff;
    private String period;
    private LocalDateTime verifiedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long operatorId;
    private Boolean reversed;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime reversedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long reversedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long voucherId;
}
