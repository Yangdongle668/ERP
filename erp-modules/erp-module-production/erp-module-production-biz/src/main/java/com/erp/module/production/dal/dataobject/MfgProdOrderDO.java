package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 生产订单（mfg_prod_order） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "mfg_prod_order", autoResultMap = true)
public class MfgProdOrderDO extends BaseDocDO {

    /** NORMAL/SAMPLE/REWORK/DISASSEMBLY */
    private String orderType;
    /** DRAFT/PENDING/PLANNED/RELEASED/IN_PROGRESS/SUSPENDED/COMPLETED/CLOSED/VOIDED */
    private String prodStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String statusBeforeSuspend;
    private Long materialId;
    /** 计划数量（基本单位） */
    private BigDecimal qty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long bomId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long routingId;
    private LocalDate planStart;
    private LocalDate planEnd;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime actualStart;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime actualEnd;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime releasedAt;
    private Integer priority;
    private String batchNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesOrderLineId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String salesOrderNo;
    /** 末道报工点合格数 */
    private BigDecimal completedQty;
    private BigDecimal scrappedQty;
    private BigDecimal finishedRequestQty;
    private BigDecimal stockedQty;
    private BigDecimal qualifiedStockedQty;
    private BigDecimal fqcRejectedQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String closeReason;
}
