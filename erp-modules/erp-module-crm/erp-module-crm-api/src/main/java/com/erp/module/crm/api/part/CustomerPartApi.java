package com.erp.module.crm.api.part;

import java.util.Optional;

/** 客户料号 ↔ 本厂物料（需求 03-02）。只匹配启用的对照。 */
public interface CustomerPartApi {

    /** 找不到时返回空，由调用方提示“客户料号「{no}」没有对照的物料，请先维护客户料号对照”（R02） */
    Optional<CustomerPartDTO> toMaterial(Long customerId, String customerPartNo);

    /** 有多个时取最近更新的启用记录（R03） */
    Optional<CustomerPartDTO> toCustomerPart(Long customerId, Long materialId);
}
