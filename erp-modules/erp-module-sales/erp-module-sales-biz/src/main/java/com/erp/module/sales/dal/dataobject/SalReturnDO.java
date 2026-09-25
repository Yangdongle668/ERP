package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 销售退货单（sal_return） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_return", autoResultMap = true)
public class SalReturnDO extends BaseDocDO {

    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rmaNo;
    private String returnReason;
    private String handling;
    private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountBase;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String complaintNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long stockInId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expectedArrivalDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String voidReason;
}
