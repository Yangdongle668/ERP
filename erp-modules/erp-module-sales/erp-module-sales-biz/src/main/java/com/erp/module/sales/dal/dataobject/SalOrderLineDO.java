package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 销售订单明细（sal_order_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_order_line", autoResultMap = true)
public class SalOrderLineDO extends BaseDO {

    private Long orderId;
    private Integer lineNo;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerPartId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPartNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private String uom;
    private BigDecimal qty;
    private BigDecimal baseQty;
    private BigDecimal price;
    private BigDecimal priceInclTax;
    private BigDecimal taxRate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String priceSource;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal costPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal marginRate;
    private Boolean belowFloor;
    private LocalDate requiredDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate promisedDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long promisedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime promisedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String promiseRemark;
    private BigDecimal noticedQty;
    private BigDecimal shippedQty;
    private BigDecimal returnedQty;
    private BigDecimal invoicedQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long quotationLineId;
    private String lineStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
