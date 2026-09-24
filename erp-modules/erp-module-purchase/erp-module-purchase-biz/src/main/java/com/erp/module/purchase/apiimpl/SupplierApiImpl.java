package com.erp.module.purchase.apiimpl;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.module.purchase.api.supplier.SupplierApi;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 占位实现。TODO(purchase): 资材模块开发供应商档案时替换为真实实现。 */
@Service
public class SupplierApiImpl implements SupplierApi {

    @Override
    public Optional<SupplierDTO> getSupplier(Long id) {
        throw BizException.of(GlobalErrorCodes.NOT_IMPLEMENTED, "供应商查询");
    }

    @Override
    public SupplierDTO validateQualified(Long id) {
        throw BizException.of(GlobalErrorCodes.NOT_IMPLEMENTED, "供应商校验");
    }
}
