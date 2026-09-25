package com.erp.module.purchase.api.requisition;

import java.util.List;

/** 采购申请（PMC 调用）。 */
public interface PurchaseRequisitionApi {

    /**
     * MRP 采购建议生成采购申请（PUR-REQ-R02）：按计划员合并为一张 MRP 类型申请单，每行记录 mrp_result_id；
     * 参数 pur.requisition.mrp-auto-submit 为是时自动提交。
     *
     * @return 生成的申请单 ID（每个计划员一张）
     */
    List<Long> createFromMrp(List<MrpPurchaseSuggestion> suggestions);
}
