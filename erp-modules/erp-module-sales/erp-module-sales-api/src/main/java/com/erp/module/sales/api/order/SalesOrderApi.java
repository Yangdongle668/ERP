package com.erp.module.sales.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 销售订单操作（PMC、出货调用）。 */
public interface SalesOrderApi {

    /**
     * PMC 交期回复（SAL-SO-R09）：写入承诺交期；承诺交期晚于要求交期时设置订单交期风险并通知业务员。
     *
     * @throws com.erp.common.exception.BizException 订单行不存在、订单不是已审核/执行中、行已关闭、日期早于今天
     */
    void updatePromisedDate(Long lineId, LocalDate date, String remark);

    /**
     * 出货数量校验：本次数量（基本单位）不超过可通知数量（含超出货比例）。
     *
     * @throws com.erp.common.exception.BizException 超出时提示“订单 {no} 第 {n} 行可通知数量为 {qty}，本次 {qty}”
     */
    void validateShipmentQty(Long lineId, BigDecimal baseQty);
}
