package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 凭证分录（fin_voucher_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_voucher_line")
public class FinVoucherLineDO extends BaseDO {

    private Long voucherId;
    private Integer lineNo;
    private String summary;
    private String accountCode;
    /** 本位币 */
    private BigDecimal debit;
    /** 本位币 */
    private BigDecimal credit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currency;
    /** 原币 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal fcAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal exchangeRate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long auxCustomerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long auxSupplierId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long auxDeptId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long auxMaterialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long auxProjectId;
    /** 来源单据类型 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceId;
}
