package com.erp.module.finance.api.query;

import java.math.BigDecimal;

/** 应付查询（资材） */
public interface PayableQueryApi {

    /** 应付余额 = 已确认未付应付 − 未冲销预付（本位币） */
    BigDecimal getBalance(Long supplierId);
}
