package com.erp.module.crm.api.part;

/** 客户料号对照（crm_customer_part） */
public record CustomerPartDTO(Long id, Long customerId, String customerPartNo, String customerPartName, String customerPartSpec,
                              String customerRevision, Long materialId) {
}
