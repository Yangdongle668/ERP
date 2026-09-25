package com.erp.module.engineering.api.ecn;

import java.util.Collection;
import java.util.List;

/**
 * ECN 影响分析扩展点：资材（在途采购）、生产（在制生产订单）、销售（未完成销售订单）各自实现。
 * 未实现的模块视为没有影响。
 */
public interface EcnImpactProvider {

    /**
     * @param componentIds 被删除/替换/改用量的旧子件
     * @param parentIds    被变更 BOM 的父件
     */
    List<EcnImpact> impacts(Collection<Long> componentIds, Collection<Long> parentIds);
}
