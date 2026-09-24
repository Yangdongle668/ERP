package com.erp.module.purchase.api.supplier;

public record SupplierDTO(
        Long id,
        String code,
        String name,
        String currency,
        Long buyerId,
        SupplierStatus status) {
}
