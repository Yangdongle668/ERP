package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 采购订单（表 pur_order） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_order", autoResultMap = true)
public class OrderDO extends BaseDocDO {

    /** STANDARD/SAMPLE */
    private String orderType;

    private Long supplierId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long supplierContactId;

    private String currency;

    private BigDecimal exchangeRate;

    private Long paymentTermId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String paymentTermSnapshot;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tradeTerm;

    private Boolean taxIncluded;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String deliveryAddress;

    private BigDecimal amount;

    private BigDecimal taxAmount;

    private BigDecimal totalAmount;

    private BigDecimal totalAmountBase;

    private Integer orderVersion;

    private Boolean hasPriceOverrun;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime sentAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String closeReason;
}
