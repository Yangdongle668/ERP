package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 销售退货明细（sal_return_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_return_line", autoResultMap = true)
public class SalReturnLineDO extends BaseDO {

    private Long returnId;
    private Integer lineNo;
    private Long orderId;
    private Long orderLineId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long shipmentLineId;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String batchNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String serialNos;
    private BigDecimal qty;
    private BigDecimal priceInclTax;
    private BigDecimal taxRate;
    private BigDecimal totalAmount;
    private BigDecimal receivedQty;
    private BigDecimal goodQty;
    private BigDecimal reworkQty;
    private BigDecimal scrapQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
