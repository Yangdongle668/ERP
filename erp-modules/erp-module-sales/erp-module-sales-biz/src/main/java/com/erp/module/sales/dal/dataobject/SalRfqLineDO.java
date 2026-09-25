package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** RFQ 明细（sal_rfq_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_rfq_line", autoResultMap = true)
public class SalRfqLineDO extends BaseDO {

    private Long rfqId;
    private Integer lineNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPartNo;
    private String description;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal annualQty;
    private String qtyBreaks;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal targetPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate requiredDate;
    private String feasibility;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String feasibilityRemark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
