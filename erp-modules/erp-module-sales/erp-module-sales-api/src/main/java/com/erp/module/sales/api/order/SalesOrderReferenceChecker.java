package com.erp.module.sales.api.order;

import java.util.Optional;

/**
 * 订单引用检查扩展点（SAL-SO-R08）：PMC / 生产（生产订单）、财务（预收款核销）等模块实现，
 * 返回引用说明时订单不能反审核。未实现的模块视为没有引用。
 */
public interface SalesOrderReferenceChecker {

    /** @return 引用说明（如“生产订单 MO-…”），没有引用时为空 */
    Optional<String> findReference(Long orderId);
}
