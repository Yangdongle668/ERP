package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 采购订单明细（表 pur_order_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_order_line")
public class OrderLineDO extends BaseDO {

    private Long orderId;

    private Integer lineNo;

    private Long materialId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierPartNo;

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
    private BigDecimal listPrice;

    private Boolean priceOverrun;

    private LocalDate requiredDate;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate confirmedDate;

    private BigDecimal receivedQty;

    private BigDecimal stockedQty;

    private BigDecimal qualifiedQty;

    private BigDecimal returnedQty;

    /** 换货退回数量（恢复未到货） */
    private BigDecimal replaceQty;

    private BigDecimal statementQty;

    /** 首次到货日期 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate firstReceivedDate;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long requisitionLineId;

    /** OPEN/RECEIVED/CLOSED */
    private String lineStatus;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastFollowUp;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime followUpAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
