package com.erp.module.crm.api.credit;

import java.math.BigDecimal;
import java.util.Collection;

/** 客户信用（需求 03-03） */
public interface CreditApi {

    /**
     * 信用检查。
     *
     * @param amountBase 本次单据金额（本位币含税）
     */
    CreditCheckResult check(Long customerId, BigDecimal amountBase, CreditCheckPoint checkPoint);

    /**
     * 重新计算客户的信用占用（调用各模块的 {@link CreditUsageProvider}）。
     * 财务应收余额变化、销售未出货订单金额变化后调用；另有每天 01:00 的全量重算。
     */
    void refresh(Collection<Long> customerIds);
}
