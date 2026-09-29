package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 付款申请明细（fin_payment_request_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_payment_request_line")
public class FinPaymentRequestLineDO extends BaseDO {

    private Long requestId;
    private Integer lineNo;
    private Long payableId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String payableNo;
    private BigDecimal amount;
    private BigDecimal paidAmount;
}
