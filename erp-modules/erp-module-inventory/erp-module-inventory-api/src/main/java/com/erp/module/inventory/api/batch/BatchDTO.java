package com.erp.module.inventory.api.batch;

import java.time.LocalDate;

public record BatchDTO(Long id, Long materialId, String batchNo, Long supplierId, String supplierBatchNo, LocalDate productionDate,
                       LocalDate expireDate, LocalDate firstInDate, String sourceType, String sourceNo, boolean concession,
                       boolean frozen, String frozenReason) {
}
