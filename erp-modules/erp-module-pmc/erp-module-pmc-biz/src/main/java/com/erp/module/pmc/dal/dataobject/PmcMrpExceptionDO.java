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

/** MRP 例外信息（pmc_mrp_exception） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_mrp_exception")
public class PmcMrpExceptionDO extends BaseDO {

    private Long runId;
    private Long materialId;
    /** EXPEDITE/DEFER/CANCEL/PAST_DUE/DISABLED */
    private String exceptionType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String docType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long docId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String docNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long docLineId;
    /** 当前供应日期 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate supplyDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate suggestedDate;
    private BigDecimal qty;
    private String message;
    /** 负责人：采购员 / 计划员 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long ownerId;
    private Boolean handled;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime pushedAt;
}
