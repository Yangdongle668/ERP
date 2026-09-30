package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 库存月流水汇总（bi_agg_inventory_monthly） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_agg_inventory_monthly")
public class BiAggInventoryMonthlyDO extends BaseDO {

    private String period;
    private String warehouseType;
    private Long materialId;
    private Long categoryId;
    private BigDecimal inAmount;
    private BigDecimal outAmount;
}
