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

/** MRP 建议（pmc_mrp_result） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_mrp_result")
public class PmcMrpResultDO extends BaseDO {

    private Long runId;
    /** PURCHASE/MAKE/OUTSOURCE */
    private String suggestionType;
    private Long materialId;
    /** 建议数量（已按批量规则取整） */
    private BigDecimal qty;
    /** 运算得到的建议数量 */
    private BigDecimal originalQty;
    private BigDecimal netRequirement;
    private LocalDate requiredDate;
    private LocalDate releaseDate;
    private LocalDate originalRequiredDate;
    private Boolean isLate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long supplierId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long plannerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long buyerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long bomId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deptId;
    /** PENDING/CONVERTED/IGNORED/SUPERSEDED */
    private String suggestionStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String convertedDocType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long convertedDocId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String convertedDocNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ignoreReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long handledBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime handledAt;
}
