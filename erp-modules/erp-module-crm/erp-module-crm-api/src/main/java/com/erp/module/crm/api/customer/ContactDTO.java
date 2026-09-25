package com.erp.module.crm.api.customer;

/** 客户联系人（crm_contact）。status：ACTIVE 在职 / LEFT 已离职 */
public record ContactDTO(Long id, Long customerId, String name, String title, String role, String email, String phone, String mobile,
                         boolean primary, String status) {
}
