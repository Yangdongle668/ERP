package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Commercial Invoice（shp_invoice） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_invoice")
public class ShpInvoiceDO extends BaseDO {

    private Long shipmentId;
    private String invoiceNo;
    private LocalDate invoiceDate;
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String billTo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String consignee;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String notifyParty;
    private String currency;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tradeTerm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String paymentTermText;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String portOfLoading;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String portOfDestination;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String vesselFlight;
    private BigDecimal totalAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String amountInWords;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bankInfo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    private Boolean invalid;
}
