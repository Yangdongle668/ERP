package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 采购日汇总（bi_agg_purchase_daily） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_agg_purchase_daily")
public class BiAggPurchaseDO extends BaseDO {

    private LocalDate statDate;
    private String period;
    private Long supplierId;
    private Long materialId;
    private Long categoryId;
    /** 采购员 */
    private Long ownerId;
    private Long deptId;
    private Long orgId;
    private BigDecimal orderAmount;
    private BigDecimal orderQty;
    private BigDecimal receiptAmount;
    private BigDecimal receiptQty;
    private Integer dueLineCount;
    private Integer onTimeLineCount;
}
