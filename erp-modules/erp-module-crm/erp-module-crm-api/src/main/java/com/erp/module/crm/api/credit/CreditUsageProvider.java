package com.erp.module.crm.api.credit;

import java.util.Collection;
import java.util.Map;

/** 信用占用扩展点（需求 03-03 1.1）：财务、销售模块各自实现；未实现的项按 0 计算。 */
public interface CreditUsageProvider {

    /** @return 客户 ID → 占用；没有数据的客户可以不返回 */
    Map<Long, CreditUsage> usage(Collection<Long> customerIds);
}
