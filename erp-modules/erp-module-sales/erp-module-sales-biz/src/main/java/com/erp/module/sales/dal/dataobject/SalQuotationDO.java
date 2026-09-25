package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 报价单（sal_quotation） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_quotation", autoResultMap = true)
public class SalQuotationDO extends BaseDocDO {

    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long contactId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long rfqId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long opportunityId;
    private String currency;
    private BigDecimal exchangeRate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tradeTerm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long paymentTermId;
    private Boolean taxIncluded;
    private LocalDate validUntil;
    private Integer revision;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentQuotationId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long rootQuotationId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String terms;
    private String quoteStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lostReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lostRemark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime sentAt;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountBase;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal minMarginRate;
    private Boolean belowFloor;
}
