package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** MRP 供需平衡明细（按运算）（pmc_mrp_balance） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_mrp_balance")
public class PmcMrpBalanceDO extends BaseDO {

    private Long runId;
    private Long materialId;
    private Integer seq;
    private LocalDate balDate;
    /** OPENING/SALES_ORDER/FORECAST/MANUAL/MPS/SAFETY_STOCK/PARENT/ALLOCATION/PURCHASE/WIP/PLANNED */
    private String entryType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String docNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentMaterialId;
    private BigDecimal demandQty;
    private BigDecimal supplyQty;
    private BigDecimal projectedQty;
    private BigDecimal safetyStock;
}
