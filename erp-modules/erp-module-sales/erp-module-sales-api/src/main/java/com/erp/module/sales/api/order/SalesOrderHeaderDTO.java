package com.erp.module.sales.api.order;

import java.math.BigDecimal;

/**
 * 销售订单单头对外视图（出货通知带出贸易条款、港口、收货地址，单证带出客户 PO、付款条件）。
 *
 * @param shipToSnapshot 收货地址快照 JSON
 */
public record SalesOrderHeaderDTO(Long orderId, String orderNo, String orderType, String status, Long customerId, String customerPoNo, String currency,
                                  BigDecimal exchangeRate, Long paymentTermId, String paymentTermSnapshot, String tradeTerm, String portOfLoading,
                                  String portOfDestination, Long shipToAddressId, String shipToSnapshot, Long billToAddressId, Long ownerId, Long deptId) {
}
