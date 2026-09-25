package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 对账明细（表 pur_statement_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_statement_line")
public class StatementLineDO extends BaseDO {

    private Long statementId;

    private Integer lineNo;

    /** GOODS/RETURN/PROCESS_FEE/ADJUST */
    private String lineType;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceType;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceLineId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderLineId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long materialId;

    private LocalDate bizDate;

    private BigDecimal qty;

    private BigDecimal priceInclTax;

    private BigDecimal taxRate;

    private BigDecimal amount;

    private BigDecimal taxAmount;

    private BigDecimal totalAmount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
