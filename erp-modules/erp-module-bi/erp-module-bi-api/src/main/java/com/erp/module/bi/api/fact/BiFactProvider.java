package com.erp.module.bi.api.fact;

import com.erp.module.bi.api.fact.BiFacts.FinanceFact;
import com.erp.module.bi.api.fact.BiFacts.InventoryFact;
import com.erp.module.bi.api.fact.BiFacts.InventoryFlowFact;
import com.erp.module.bi.api.fact.BiFacts.ProductionFact;
import com.erp.module.bi.api.fact.BiFacts.PurchaseFact;
import com.erp.module.bi.api.fact.BiFacts.QualityFact;
import com.erp.module.bi.api.fact.BiFacts.SalesFact;

import java.time.LocalDate;
import java.util.List;

/**
 * BI 数据来源扩展点（需求 13-01 BI-DATA-R01 / R02）。各业务模块在自己的 biz 中实现并注册为 Bean，只查询本模块的表，
 * 覆盖自己负责的部分（其余保持默认空列表）；BI 汇总时把所有提供者的同类事实按粒度相加。
 * <p>调用时 BI 已跳过数据权限（DataScopes.ignore），提供者返回全部数据；日期区间为闭区间。
 */
public interface BiFactProvider {

    default List<SalesFact> salesFacts(LocalDate from, LocalDate to) {
        return List.of();
    }

    default List<PurchaseFact> purchaseFacts(LocalDate from, LocalDate to) {
        return List.of();
    }

    default List<ProductionFact> productionFacts(LocalDate from, LocalDate to) {
        return List.of();
    }

    default List<QualityFact> qualityFacts(LocalDate from, LocalDate to) {
        return List.of();
    }

    /** 当前库存（每日快照） */
    default List<InventoryFact> inventorySnapshot() {
        return List.of();
    }

    default List<InventoryFlowFact> inventoryFlows(String fromPeriod, String toPeriod) {
        return List.of();
    }

    /** 期间（yyyyMM）往来余额 */
    default List<FinanceFact> financeFacts(String period) {
        return List.of();
    }
}
