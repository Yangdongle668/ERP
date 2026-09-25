package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 调价明细（表 pur_price_adjust_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_price_adjust_line")
public class PriceAdjustLineDO extends BaseDO {

    private Long adjustId;

    private Integer lineNo;

    private Long materialId;

    private BigDecimal minQty;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal oldPrice;

    private BigDecimal newPrice;

    private BigDecimal taxRate;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal changePct;

    private LocalDate effectiveFrom;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate effectiveTo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
