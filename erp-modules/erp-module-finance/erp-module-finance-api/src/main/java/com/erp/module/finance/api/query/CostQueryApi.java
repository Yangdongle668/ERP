package com.erp.module.finance.api.query;

import java.math.BigDecimal;
import java.util.Optional;

/** 成本查询（销售报表、BI）：只返回已成功计算的期间结果 */
public interface CostQueryApi {

    /** 物料期间加权单价（本位币 / 基本单位） */
    Optional<BigDecimal> getUnitCost(Long materialId, String period);

    /** 生产订单期间完工成本（本位币） */
    Optional<BigDecimal> getOrderCost(Long prodOrderId, String period);
}
