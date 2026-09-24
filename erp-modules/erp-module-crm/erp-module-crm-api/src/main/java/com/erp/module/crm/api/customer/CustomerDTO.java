package com.erp.module.crm.api.customer;

public record CustomerDTO(
        Long id,
        String code,
        String name,
        String nameEn,
        String country,
        String currency,
        Long ownerId,
        CustomerStatus status) {
}
