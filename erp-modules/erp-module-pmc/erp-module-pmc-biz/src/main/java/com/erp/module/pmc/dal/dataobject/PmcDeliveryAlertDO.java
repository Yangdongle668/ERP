package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 交期预警（pmc_delivery_alert） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pmc_delivery_alert", autoResultMap = true)
public class PmcDeliveryAlertDO extends BaseDO {

    private Long orderLineId;
    private Long orderId;
    private String orderNo;
    private Integer orderLineNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesOwnerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesDeptId;
    private Long materialId;
    private BigDecimal openQty;
    private LocalDate promisedDate;
    private LocalDate estimatedDate;
    private Integer delayDays;
    /** INFO/WARNING/CRITICAL */
    private String alertLevel;
    /** NO_STOCK_NO_WO/MATERIAL_SHORTAGE/CAPACITY/WO_DELAY */
    private String cause;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String causeDetail;
    /** OPEN/HANDLED/IGNORED/CLOSED */
    private String handleStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String handleRemark;
    /** 处理时的延期天数 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer handledDelayDays;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long handledBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime handledAt;
    private LocalDateTime calculatedAt;
}
