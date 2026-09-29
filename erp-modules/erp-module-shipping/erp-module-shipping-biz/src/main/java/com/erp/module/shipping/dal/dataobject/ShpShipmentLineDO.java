package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 出货单明细（一行一个批次）（shp_shipment_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_shipment_line")
public class ShpShipmentLineDO extends BaseDO {

    private Long shipmentId;
    private Integer lineNo;
    private Long noticeLineId;
    private Long orderId;
    private String orderNo;
    private Long orderLineId;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPartNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private String uom;
    /** 订单单位 */
    private BigDecimal qty;
    private BigDecimal baseQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String batchNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String serialNos;
    private BigDecimal priceInclTax;
    private BigDecimal taxRate;
    /** 不含税 */
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal outQty;
}
