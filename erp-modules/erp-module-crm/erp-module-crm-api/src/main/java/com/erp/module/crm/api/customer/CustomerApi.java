package com.erp.module.crm.api.customer;

import java.util.Optional;

public interface CustomerApi {

    Optional<CustomerDTO> getCustomer(Long id);

    /**
     * 校验客户可以下正式订单（状态为 ACTIVE）。
     *
     * @throws com.erp.common.exception.BizException 不存在、非正式客户或黑名单
     */
    CustomerDTO validateCanOrder(Long id);
}
