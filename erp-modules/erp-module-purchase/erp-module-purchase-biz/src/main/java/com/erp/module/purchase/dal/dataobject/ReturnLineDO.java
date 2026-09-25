package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 采购退货明细（表 pur_return_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_return_line")
public class ReturnLineDO extends BaseDO {

    private Long returnId;

    private Integer lineNo;

    private Long receiptLineId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderLineId;

    private Long materialId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String batchNo;

    /** 基本单位 */
    private BigDecimal qty;

    private BigDecimal priceInclTax;

    private BigDecimal taxRate;

    private BigDecimal totalAmount;

    private BigDecimal outQty;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate outDate;

    private BigDecimal statementQty;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
