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

/** 需求池（pmc_demand） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_demand")
public class PmcDemandDO extends BaseDO {

    /** SALES_ORDER/FORECAST/MANUAL */
    private String demandType;
    /** 销售订单 / 预测单 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceId;
    /** 销售订单行 / 预测行 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceLineId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer sourceLineNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    /** 业务员 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesOwnerId;
    private Long materialId;
    /** 需求数量（基本单位） */
    private BigDecimal qty;
    /** 已满足：订单已出货 / 预测已冲销 */
    private BigDecimal fulfilledQty;
    private BigDecimal openQty;
    /** 需求日期：承诺交期优先 */
    private LocalDate requiredDate;
    /** 客户要求交期 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate customerDate;
    /** 承诺交期 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate promisedDate;
    private Integer priority;
    /** OPEN/CLOSED */
    private String demandStatus;
    /** 销售订单：PENDING 待回复 / REPLIED 已回复 / REREPLY 需重新回复 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String replyStatus;
    /** 回复时的数量（变更检测） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal repliedQty;
    /** 回复时的要求交期 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate repliedCustomerDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String replyRemark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long repliedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime repliedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
