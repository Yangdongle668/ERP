package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** MRP 需求追溯（pmc_mrp_pegging） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_mrp_pegging")
public class PmcMrpPeggingDO extends BaseDO {

    private Long runId;
    private Long resultId;
    /** SALES_ORDER/FORECAST/MANUAL/MPS/SAFETY_STOCK/PARENT/ALLOCATION */
    private String demandType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentMaterialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentResultId;
    private BigDecimal qty;
    private LocalDate requiredDate;
}
