package com.erp.module.purchase.apiimpl;

import com.erp.module.purchase.api.supplier.SupplierApi;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.purchase.api.supplier.SupplierStatus;
import com.erp.module.purchase.service.supplier.SupplierService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 供应商对外接口（需求 07-资材 README 第 11 节） */
@Service
public class SupplierApiImpl implements SupplierApi {

    private final SupplierService supplierService;

    public SupplierApiImpl(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @Override
    public Optional<SupplierDTO> getSupplier(Long id) {
        return supplierService.find(id).map(SupplierService::toDto);
    }

    @Override
    public SupplierDTO validateQualified(Long id) {
        return SupplierService.toDto(supplierService.validateQualified(id));
    }

    @Override
    public List<SupplierDTO> search(String keyword, Collection<SupplierStatus> statuses, int limit) {
        return supplierService.searchDto(keyword, statuses, limit);
    }

    @Override
    public Optional<SupplierDTO> getDefaultSupplier(Long materialId) {
        return supplierService.defaultSupplier(materialId).map(SupplierService::toDto);
    }
}
