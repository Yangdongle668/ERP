package com.erp.module.purchase.api.supplier;

import java.util.Optional;

public interface SupplierApi {

    Optional<SupplierDTO> getSupplier(Long id);

    /** 校验供应商为合格供应商且资质有效。 */
    SupplierDTO validateQualified(Long id);
}
