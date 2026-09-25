package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 报价单明细（sal_quotation_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_quotation_line", autoResultMap = true)
public class SalQuotationLineDO extends BaseDO {

    private Long quotationId;
    private Integer lineNo;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPartNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private String uom;
    private BigDecimal minQty;
    private BigDecimal price;
    private BigDecimal taxRate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal costPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal marginRate;
    private Boolean belowFloor;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal moq;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer leadTimeDays;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal toolingFee;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long rfqLineId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
