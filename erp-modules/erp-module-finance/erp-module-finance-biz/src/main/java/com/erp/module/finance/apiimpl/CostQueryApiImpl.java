package com.erp.module.finance.apiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.finance.api.query.CostQueryApi;
import com.erp.module.finance.dal.dataobject.FinCostMaterialDO;
import com.erp.module.finance.dal.dataobject.FinCostOrderDO;
import com.erp.module.finance.dal.dataobject.FinCostRunDO;
import com.erp.module.finance.dal.mapper.FinCostMaterialMapper;
import com.erp.module.finance.dal.mapper.FinCostOrderMapper;
import com.erp.module.finance.dal.mapper.FinCostRunMapper;
import com.erp.module.sales.api.cost.SalesCostProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 成本查询（只依赖 Mapper，避免循环依赖）：只返回已成功计算的期间结果。
 * 同时作为销售的 {@link SalesCostProvider}：取最近一个成功计算期间的物料加权单价（实际成本），没有时销售按最新采购价兜底。
 */
@Component
public class CostQueryApiImpl implements CostQueryApi, SalesCostProvider {

    private final FinCostRunMapper runMapper;
    private final FinCostMaterialMapper materialMapper;
    private final FinCostOrderMapper orderMapper;

    public CostQueryApiImpl(FinCostRunMapper runMapper, FinCostMaterialMapper materialMapper, FinCostOrderMapper orderMapper) {
        this.runMapper = runMapper;
        this.materialMapper = materialMapper;
        this.orderMapper = orderMapper;
    }

    private boolean calculated(String period) {
        return period != null && runMapper.selectCount(new LambdaQueryWrapper<FinCostRunDO>().eq(FinCostRunDO::getPeriod, period)
                .eq(FinCostRunDO::getRunStatus, "SUCCESS")) > 0;
    }

    @Override
    public Optional<BigDecimal> getUnitCost(Long materialId, String period) {
        if (materialId == null || !calculated(period)) return Optional.empty();
        return materialMapper.selectList(new LambdaQueryWrapper<FinCostMaterialDO>().eq(FinCostMaterialDO::getPeriod, period)
                .eq(FinCostMaterialDO::getMaterialId, materialId)).stream().map(FinCostMaterialDO::getUnitCost).filter(Objects::nonNull).findFirst();
    }

    @Override
    public Optional<BigDecimal> getOrderCost(Long prodOrderId, String period) {
        if (prodOrderId == null || !calculated(period)) return Optional.empty();
        return orderMapper.selectList(new LambdaQueryWrapper<FinCostOrderDO>().eq(FinCostOrderDO::getPeriod, period)
                .eq(FinCostOrderDO::getProdOrderId, prodOrderId)).stream().map(FinCostOrderDO::getFinishedCost).filter(Objects::nonNull).findFirst();
    }

    @Override
    public Map<Long, BigDecimal> unitCosts(Collection<Long> materialIds) {
        List<Long> ids = materialIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String period = runMapper.selectList(new LambdaQueryWrapper<FinCostRunDO>().eq(FinCostRunDO::getRunStatus, "SUCCESS")
                .orderByDesc(FinCostRunDO::getPeriod).last("LIMIT 1")).stream().findFirst().map(FinCostRunDO::getPeriod).orElse(null);
        if (period == null) return Map.of();
        return materialMapper.selectList(new LambdaQueryWrapper<FinCostMaterialDO>().eq(FinCostMaterialDO::getPeriod, period)
                        .in(FinCostMaterialDO::getMaterialId, ids)).stream()
                .filter(m -> m.getUnitCost() != null && m.getUnitCost().signum() > 0)
                .collect(Collectors.toMap(FinCostMaterialDO::getMaterialId, FinCostMaterialDO::getUnitCost, (a, b) -> a));
    }
}
