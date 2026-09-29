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

/** 出货通知（shp_notice） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "shp_notice", autoResultMap = true)
public class ShpNoticeDO extends BaseDocDO {

    private Long customerId;
    private String currency;
    /** 计划出货日期 */
    private LocalDate shipDate;
    /** 字典 shp_transport_mode */
    private String transportMode;
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
    private String notifyParty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long forwarderId;
    private Long warehouseId;
    private Boolean oqcRequired;
    /** PENDING / PASSED / REJECTED */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String oqcResult;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String oqcInspectionIds;
    /** 价税合计（原币） */
    private BigDecimal totalAmount;
    private BigDecimal totalAmountBase;
    private Boolean creditWarning;
    private Boolean prepaymentUnpaid;
    /** DRAFT/PENDING/APPROVED/PICKING/PACKED/OQC/READY/SHIPPED/CLOSED/VOIDED */
    private String noticeStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime approvedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String closeReason;
}
