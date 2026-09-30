package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 销售日汇总（bi_agg_sales_daily） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_agg_sales_daily")
public class BiAggSalesDO extends BaseDO {

    private LocalDate statDate;
    private String period;
    private Long customerId;
    private Long materialId;
    private Long categoryId;
    private String country;
    /** 业务员 */
    private Long ownerId;
    private Long deptId;
    private Long orgId;
    private BigDecimal orderAmount;
    private BigDecimal shipAmount;
    private BigDecimal shipQty;
    private BigDecimal shipCost;
    /** 已有成本的出货额（毛利率分母） */
    private BigDecimal costedShipAmount;
    private BigDecimal returnAmount;
    private Integer shipLineCount;
    private Integer onTimeLineCount;
    private BigDecimal receiptAmount;
}
