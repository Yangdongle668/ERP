package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 出货单（shp_shipment） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "shp_shipment", autoResultMap = true)
public class ShpShipmentDO extends BaseDocDO {

    private Long noticeId;
    private String noticeNo;
    private Long customerId;
    private String currency;
    private BigDecimal exchangeRate;
    private LocalDate shipDate;
    private String transportMode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tradeTerm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String portOfLoading;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String portOfDestination;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shipToSnapshot;
    private Long warehouseId;
    private BigDecimal totalQty;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountBase;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer cartonCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal grossWeight;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netWeight;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal cbm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long stockOutId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long packingListId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long invoiceId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customsId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long forwarderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String containerNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sealNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String blNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate blDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate etd;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate eta;
    /** 字典 shp_logistics_status */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String logisticsStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime logisticsUpdatedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime signedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String signedBy;
    private Boolean creditWarning;
    private Boolean prepaymentUnpaid;
    /** DRAFT/PENDING/SUBMITTED/SHIPPED/COMPLETED/VOIDED */
    private String shipmentStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime shippedAt;
    private Boolean etaReminded;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String voidReason;
}
