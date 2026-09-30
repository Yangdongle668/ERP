package com.erp.module.sales.service.bi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.bi.api.fact.BiFactProvider;
import com.erp.module.bi.api.fact.BiFacts.SalesFact;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import com.erp.module.sales.dal.dataobject.SalReturnDO;
import com.erp.module.sales.dal.dataobject.SalReturnLineDO;
import com.erp.module.sales.dal.mapper.SalOrderLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.dal.mapper.SalReturnLineMapper;
import com.erp.module.sales.dal.mapper.SalReturnMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** BI 销售事实（需求 13-01）：接单额（订单审核日，行价税合计 × 订单汇率）、退货金额（退货单日期，已收货数量 × 不含税单价 × 汇率） */
@Component
public class SalesBiFactProvider implements BiFactProvider {

    static final List<DocStatus> EFFECTIVE = List.of(DocStatus.APPROVED, DocStatus.IN_PROGRESS, DocStatus.COMPLETED, DocStatus.CLOSED);

    private final SalOrderMapper orderMapper;
    private final SalOrderLineMapper orderLineMapper;
    private final SalReturnMapper returnMapper;
    private final SalReturnLineMapper returnLineMapper;

    public SalesBiFactProvider(SalOrderMapper orderMapper, SalOrderLineMapper orderLineMapper, SalReturnMapper returnMapper,
                               SalReturnLineMapper returnLineMapper) {
        this.orderMapper = orderMapper;
        this.orderLineMapper = orderLineMapper;
        this.returnMapper = returnMapper;
        this.returnLineMapper = returnLineMapper;
    }

    @Override
    public List<SalesFact> salesFacts(LocalDate from, LocalDate to) {
        List<SalesFact> facts = new ArrayList<>();
        Map<Long, SalOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<SalOrderDO>().in(SalOrderDO::getStatus, EFFECTIVE)
                        .ge(SalOrderDO::getApprovedAt, from.atStartOfDay()).lt(SalOrderDO::getApprovedAt, to.plusDays(1).atStartOfDay()))
                .stream().collect(Collectors.toMap(SalOrderDO::getId, Function.identity()));
        if (!orders.isEmpty()) {
            for (SalOrderLineDO l : orderLineMapper.selectList(new LambdaQueryWrapper<SalOrderLineDO>().in(SalOrderLineDO::getOrderId, orders.keySet()))) {
                SalOrderDO o = orders.get(l.getOrderId());
                BigDecimal amount = nz(l.getTotalAmount()).multiply(rate(o.getExchangeRate())).setScale(2, RoundingMode.HALF_UP);
                facts.add(new SalesFact(o.getApprovedAt().toLocalDate(), o.getCustomerId(), l.getMaterialId(), o.getOwnerId(), o.getDeptId(), amount,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO));
            }
        }
        Map<Long, SalReturnDO> returns = returnMapper.selectList(new LambdaQueryWrapper<SalReturnDO>().ne(SalReturnDO::getStatus, DocStatus.VOIDED)
                .between(SalReturnDO::getDocDate, from, to)).stream().collect(Collectors.toMap(SalReturnDO::getId, Function.identity()));
        if (!returns.isEmpty()) {
            for (SalReturnLineDO l : returnLineMapper.selectList(new LambdaQueryWrapper<SalReturnLineDO>().in(SalReturnLineDO::getReturnId, returns.keySet())
                    .gt(SalReturnLineDO::getReceivedQty, BigDecimal.ZERO))) {
                SalReturnDO r = returns.get(l.getReturnId());
                BigDecimal net = nz(l.getPriceInclTax()).divide(BigDecimal.ONE.add(nz(l.getTaxRate())), 6, RoundingMode.HALF_UP);
                BigDecimal amount = nz(l.getReceivedQty()).multiply(net).multiply(rate(r.getExchangeRate())).setScale(2, RoundingMode.HALF_UP);
                facts.add(new SalesFact(r.getDocDate(), r.getCustomerId(), l.getMaterialId(), r.getOwnerId(), r.getDeptId(), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, amount, 0, 0, BigDecimal.ZERO));
            }
        }
        return facts;
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static BigDecimal rate(BigDecimal r) {
        return r == null || r.signum() == 0 ? BigDecimal.ONE : r;
    }
}
