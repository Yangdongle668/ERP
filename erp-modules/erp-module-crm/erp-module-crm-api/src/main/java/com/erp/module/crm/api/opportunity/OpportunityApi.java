package com.erp.module.crm.api.opportunity;

/** 商机（需求 03-05）：由销售模块在报价、订单节点回调 */
public interface OpportunityApi {

    /** 报价单关联了商机时调用：商机在“报价”之前的阶段自动推进到“报价”（R03） */
    void onQuotationCreated(Long opportunityId);

    /** 订单（来源报价关联了商机）审核后调用：商机自动赢单并记录订单号（R04） */
    void onOrderApproved(Long opportunityId, String orderNo);
}
