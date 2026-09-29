package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 出货计划明细（pmc_shipping_plan_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_shipping_plan_line")
public class PmcShippingPlanLineDO extends BaseDO {

    private Long planId;
    private Integer lineNo;
    private Long orderLineId;
    private Long orderId;
    private String orderNo;
    private Integer orderLineNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    private Long materialId;
    /** 承诺交期，无则要求交期 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate dueDate;
    private BigDecimal openQty;
    private BigDecimal availableQty;
    private BigDecimal planQty;
    private LocalDate planShipDate;
    /** SEA/AIR/EXPRESS/LAND */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String transportMode;
    private BigDecimal noticedQty;
    /** PLANNED/NOTICED/CANCELED */
    private String lineStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
