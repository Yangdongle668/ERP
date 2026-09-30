package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 外币期末重估（fin_fx_revaluation） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_fx_revaluation")
public class FinFxRevaluationDO extends BaseDO {

    private String period;
    /** AR/AP/BANK */
    private String docType;
    private Long docId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String docNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long partnerId;
    private String currency;
    private BigDecimal fcBalance;
    private BigDecimal bookBase;
    private BigDecimal periodEndRate;
    private BigDecimal revaluedBase;
    private BigDecimal diff;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long voucherId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long reversalVoucherId;
}
