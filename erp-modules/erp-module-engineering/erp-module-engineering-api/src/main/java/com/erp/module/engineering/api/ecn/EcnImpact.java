package com.erp.module.engineering.api.ecn;

import java.math.BigDecimal;

/**
 * ECN 影响分析的一行（需求 05-05 eng_ecn_impact）。
 *
 * @param impactType PURCHASE 在途采购 / WIP 在制生产订单 / SALES 未完成销售订单（库存由研发工程直接查询仓库）
 * @param docNo      相关单据号
 */
public record EcnImpact(Long materialId, String impactType, String docNo, BigDecimal qty) {
}
