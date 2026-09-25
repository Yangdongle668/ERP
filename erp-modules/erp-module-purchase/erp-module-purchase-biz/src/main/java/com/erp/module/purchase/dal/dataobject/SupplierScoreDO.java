package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.purchase.service.score.ScoreStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 供应商评估（表 pur_supplier_score） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_supplier_score")
public class SupplierScoreDO extends BaseDO {

    private Long supplierId;

    private String period;

    private Integer lotCount;

    private Integer lotPassCount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal qualityScore;

    private Integer dueLineCount;

    private Integer ontimeLineCount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal deliveryScore;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal priceScore;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal serviceScore;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal totalScore;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String grade;

    /** CALCULATED/SCORED/PUBLISHED */
    private ScoreStatus scoreStatus;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String scoreComment;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unpublishReason;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime publishedAt;
}
