package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 销售订单（sal_order） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_order", autoResultMap = true)
public class SalOrderDO extends BaseDocDO {

    private String orderType;
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long contactId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPoNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate customerPoDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long quotationId;
    private String currency;
    private BigDecimal exchangeRate;
    private Boolean taxIncluded;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long paymentTermId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String paymentTermSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tradeTerm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String portOfLoading;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String portOfDestination;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long shipToAddressId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shipToSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long billToAddressId;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountBase;
    private BigDecimal shippedAmount;
    private BigDecimal receivedAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal minMarginRate;
    private Boolean belowFloor;
    private Boolean creditWarning;
    private Integer orderVersion;
    private Boolean deliveryRisk;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String closeReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String terms;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime approvedAt;
}
