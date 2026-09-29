package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 出货通知明细（shp_notice_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_notice_line")
public class ShpNoticeLineDO extends BaseDO {

    private Long noticeId;
    private Integer lineNo;
    private Long orderId;
    private String orderNo;
    private Long orderLineId;
    private Integer orderLineNo;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPartNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    /** 订单单位 */
    private String uom;
    /** 通知数量（订单单位） */
    private BigDecimal qty;
    private BigDecimal baseQty;
    /** 含税单价（每订单单位） */
    private BigDecimal priceInclTax;
    /** 含税单价（每基本单位） */
    private BigDecimal basePriceInclTax;
    private BigDecimal taxRate;
    private BigDecimal totalAmount;
    private Boolean oqcRequired;
    /** 已拣货（基本单位） */
    private BigDecimal pickedQty;
    private BigDecimal packedQty;
    private BigDecimal shippedQty;
    private BigDecimal shortageQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long shippingPlanLineId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
