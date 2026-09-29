package com.erp.module.finance.api.query;

import java.math.BigDecimal;

/** 应收查询（CRM 信用、销售），金额为本位币 */
public interface ReceivableQueryApi {

    /** 应收余额 = 已确认未核销应收 − 已确认未核销预收（本位币） */
    BigDecimal getBalance(Long customerId);

    /** 逾期应收（到期日早于今天的未核销金额，本位币） */
    BigDecimal getOverdue(Long customerId);

    /** 订单已收款（原币）：核销到该订单应收的金额 + 该订单的预收款 */
    BigDecimal getOrderReceived(Long orderId);
}
