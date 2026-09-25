package com.erp.module.crm.api.customer;

/** 客户地址（crm_address）。type：SHIP_TO 收货 / BILL_TO 开票 / NOTIFY 通知方 */
public record AddressDTO(Long id, Long customerId, String type, String companyName, String contactName, String phone, String country,
                         String province, String city, String zip, String addressLine, boolean isDefault) {
}
