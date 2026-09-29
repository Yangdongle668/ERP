package com.erp.module.finance.apiimpl;

import com.erp.module.finance.api.query.CostQueryApi;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** 成本查询：成本核算（12-07）上线前没有已计算的期间，均返回空 */
@Component
public class CostQueryApiImpl implements CostQueryApi {

    @Override
    public Optional<BigDecimal> getUnitCost(Long materialId, String period) {
        return Optional.empty();
    }

    @Override
    public Optional<BigDecimal> getOrderCost(Long prodOrderId, String period) {
        return Optional.empty();
    }
}
