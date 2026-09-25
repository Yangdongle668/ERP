package com.erp.module.sales.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import com.erp.module.sales.dal.mapper.SalOrderLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户未出货订单金额（本位币含税，CRM 信用占用）：已审核、执行中订单未关闭行的未出货数量 × 含税单价 × 汇率；补货订单不计。
 * 只依赖 Mapper，避免与 CRM 信用服务形成循环依赖。
 */
@Component
public class OpenAmountCalculator {

    private final SalOrderMapper orderMapper;
    private final SalOrderLineMapper lineMapper;

    public OpenAmountCalculator(SalOrderMapper orderMapper, SalOrderLineMapper lineMapper) {
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
    }

    public Map<Long, BigDecimal> openAmounts(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) return Map.of();
        List<SalOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<SalOrderDO>().in(SalOrderDO::getCustomerId, customerIds)
                .in(SalOrderDO::getStatus, OrderService.ACTIVE).ne(SalOrderDO::getOrderType, OrderService.REPLACEMENT));
        if (orders.isEmpty()) return Map.of();
        Map<Long, SalOrderDO> byId = orders.stream().collect(Collectors.toMap(SalOrderDO::getId, o -> o));
        Map<Long, BigDecimal> map = new HashMap<>();
        for (SalOrderLineDO l : lineMapper.selectList(new LambdaQueryWrapper<SalOrderLineDO>().in(SalOrderLineDO::getOrderId, byId.keySet())
                .eq(SalOrderLineDO::getLineStatus, OrderService.OPEN))) {
            SalOrderDO o = byId.get(l.getOrderId());
            map.merge(o.getCustomerId(), openAmount(l).multiply(o.getExchangeRate()), BigDecimal::add);
        }
        map.replaceAll((k, v) -> v.setScale(2, RoundingMode.HALF_UP));
        return map;
    }

    /** 订单行未出货金额（原币含税） */
    static BigDecimal openAmount(SalOrderLineDO l) {
        BigDecimal open = l.getBaseQty().subtract(l.getShippedQty()).max(BigDecimal.ZERO);
        if (open.signum() == 0 || l.getBaseQty().signum() == 0) return BigDecimal.ZERO;
        return l.getTotalAmount().multiply(open).divide(l.getBaseQty(), 2, RoundingMode.HALF_UP);
    }
}
