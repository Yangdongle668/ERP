package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 生产日汇总（bi_agg_production_daily） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_agg_production_daily")
public class BiAggProductionDO extends BaseDO {

    private LocalDate statDate;
    private String period;
    /** 车间 */
    private Long deptId;
    private Long materialId;
    private Long categoryId;
    private BigDecimal planQty;
    private BigDecimal goodQty;
    private BigDecimal defectQty;
    private BigDecimal scrapQty;
    private BigDecimal workHours;
    private BigDecimal stdHours;
    private BigDecimal inQty;
    private BigDecimal firstPassQty;
    private Integer delayedOrderCount;
}
