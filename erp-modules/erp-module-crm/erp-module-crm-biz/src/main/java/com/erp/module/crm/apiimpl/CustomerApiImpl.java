package com.erp.module.crm.apiimpl;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.customer.CustomerDTO;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 占位实现。TODO(crm): CRM 模块开发客户档案时替换为真实实现。 */
@Service
public class CustomerApiImpl implements CustomerApi {

    @Override
    public Optional<CustomerDTO> getCustomer(Long id) {
        throw BizException.of(GlobalErrorCodes.NOT_IMPLEMENTED, "客户查询");
    }

    @Override
    public CustomerDTO validateCanOrder(Long id) {
        throw BizException.of(GlobalErrorCodes.NOT_IMPLEMENTED, "客户校验");
    }
}
