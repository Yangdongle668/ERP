package com.erp.module.production.apiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.production.api.cost.ProductionCostApi;
import com.erp.module.production.dal.dataobject.MfgFinishDO;
import com.erp.module.production.dal.dataobject.MfgIssueDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgReportDO;
import com.erp.module.production.dal.dataobject.MfgReturnDO;
import com.erp.module.production.dal.mapper.MfgFinishMapper;
import com.erp.module.production.dal.mapper.MfgIssueMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgReportMapper;
import com.erp.module.production.dal.mapper.MfgReturnMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 成本核算用生产数据（财务 12-07） */
@Service
public class ProductionCostApiImpl implements ProductionCostApi {

    private final MfgIssueMapper issueMapper;
    private final MfgReturnMapper returnMapper;
    private final MfgFinishMapper finishMapper;
    private final MfgProdOrderMapper orderMapper;
    private final MfgReportMapper reportMapper;

    public ProductionCostApiImpl(MfgIssueMapper issueMapper, MfgReturnMapper returnMapper, MfgFinishMapper finishMapper, MfgProdOrderMapper orderMapper,
                                 MfgReportMapper reportMapper) {
        this.issueMapper = issueMapper;
        this.returnMapper = returnMapper;
        this.finishMapper = finishMapper;
        this.orderMapper = orderMapper;
        this.reportMapper = reportMapper;
    }

    @Override
    public Map<Long, Long> getOrderIdsBySource(String sourceType, Collection<Long> sourceIds) {
        Map<Long, Long> map = new HashMap<>();
        List<Long> ids = sourceIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return map;
        switch (sourceType) {
            case SOURCE_ISSUE -> issueMapper.selectBatchIds(ids).forEach((MfgIssueDO i) -> map.put(i.getId(), i.getProdOrderId()));
            case SOURCE_RETURN -> returnMapper.selectBatchIds(ids).forEach((MfgReturnDO r) -> map.put(r.getId(), r.getProdOrderId()));
            case SOURCE_FINISH -> finishMapper.selectBatchIds(ids).forEach((MfgFinishDO f) -> map.put(f.getId(), f.getProdOrderId()));
            default -> {
            }
        }
        return map;
    }

    @Override
    public List<CostOrder> getOrders(Collection<Long> prodOrderIds) {
        List<Long> ids = prodOrderIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return List.of();
        List<CostOrder> list = new ArrayList<>();
        for (MfgProdOrderDO o : orderMapper.selectBatchIds(ids)) {
            list.add(new CostOrder(o.getId(), o.getDocNo(), o.getMaterialId(), o.getOrderType(), o.getProdStatus(), o.getQty(), o.getDeptId()));
        }
        return list;
    }

    @Override
    public List<WorkHour> getWorkHours(LocalDate from, LocalDate to) {
        List<MfgReportDO> reports = reportMapper.selectList(new LambdaQueryWrapper<MfgReportDO>().eq(MfgReportDO::getStatus, DocStatus.APPROVED)
                .ge(MfgReportDO::getReportDate, from).le(MfgReportDO::getReportDate, to));
        Map<Long, MfgProdOrderDO> orders = new HashMap<>();
        List<Long> orderIds = reports.stream().map(MfgReportDO::getProdOrderId).distinct().toList();
        if (!orderIds.isEmpty()) orderMapper.selectBatchIds(orderIds).forEach(o -> orders.put(o.getId(), o));
        Map<String, BigDecimal[]> sums = new HashMap<>();
        Map<String, Long[]> keys = new HashMap<>();
        for (MfgReportDO r : reports) {
            MfgProdOrderDO o = orders.get(r.getProdOrderId());
            Long dept = r.getDeptId() != null ? r.getDeptId() : o == null ? null : o.getDeptId();
            String key = r.getProdOrderId() + "|" + dept;
            keys.putIfAbsent(key, new Long[]{r.getProdOrderId(), dept});
            BigDecimal[] s = sums.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            s[0] = s[0].add(r.getWorkHours() == null ? BigDecimal.ZERO : r.getWorkHours());
            s[1] = s[1].add(r.getGoodQty() == null ? BigDecimal.ZERO : r.getGoodQty());
        }
        List<WorkHour> list = new ArrayList<>();
        sums.forEach((k, s) -> list.add(new WorkHour(keys.get(k)[0], keys.get(k)[1], s[0], s[1])));
        return list;
    }
}
