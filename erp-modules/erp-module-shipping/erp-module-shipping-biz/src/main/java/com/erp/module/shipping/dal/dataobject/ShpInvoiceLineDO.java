package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** Invoice 明细（shp_invoice_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_invoice_line")
public class ShpInvoiceLineDO extends BaseDO {

    private Long invoiceId;
    private Integer lineNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long shipmentLineId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPoNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPartNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String hsCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String origin;
    private BigDecimal qty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String uom;
    private BigDecimal unitPrice;
    private BigDecimal amount;
}
