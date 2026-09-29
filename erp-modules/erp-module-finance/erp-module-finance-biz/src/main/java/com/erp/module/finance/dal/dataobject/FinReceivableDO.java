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

/** 应收单（fin_receivable） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "fin_receivable", autoResultMap = true)
public class FinReceivableDO extends BaseDocDO {

    /** SALES/SALES_RETURN/DISCOUNT/OTHER */
    private String arType;
    private Long customerId;
    private String currency;
    private BigDecimal exchangeRate;
    /** 不含税（原币，红字为负） */
    private BigDecimal amount;
    private BigDecimal taxAmount;
    /** 价税合计（原币） */
    private BigDecimal totalAmount;
    private BigDecimal totalAmountBase;
    /** 业务日期，决定会计期间 */
    private LocalDate bizDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate dueDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long paymentTermId;
    /** 主订单（预收冲销匹配） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderId;
    /** 来源子单据（退货入库单等，事件幂等） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceRefId;
    /** 提单日期（到期日重算） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate blDate;
    private BigDecimal verifiedAmount;
    private BigDecimal invoicedAmount;
    /** DRAFT/PENDING/CONFIRMED/VOIDED */
    private String arStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime confirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String voidReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long voucherId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
