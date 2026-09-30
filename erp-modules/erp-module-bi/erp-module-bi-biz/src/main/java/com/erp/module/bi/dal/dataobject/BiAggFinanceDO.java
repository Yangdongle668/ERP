package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 往来月汇总（bi_agg_finance_monthly） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_agg_finance_monthly")
public class BiAggFinanceDO extends BaseDO {

    private String period;
    /** CUSTOMER/SUPPLIER */
    private String partnerType;
    private Long partnerId;
    private Long ownerId;
    private Long deptId;
    private Long orgId;
    private BigDecimal beginBalance;
    private BigDecimal addAmount;
    private BigDecimal settleAmount;
    private BigDecimal endBalance;
    private BigDecimal overdueAmount;
}
