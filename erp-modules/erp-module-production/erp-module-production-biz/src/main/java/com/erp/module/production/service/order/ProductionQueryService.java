package com.erp.module.production.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.production.api.order.ComponentDemandDTO;
import com.erp.module.production.api.order.OpenOrderDTO;
import com.erp.module.production.api.order.ProductionQueryApi;
import com.erp.module.production.api.order.ProgressDTO;
import com.erp.module.production.api.order.WipDTO;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderMaterialDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderOperationDO;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMaterialMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderOperationMapper;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.ProdStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 生产查询对外实现（PMC、研发工程、销售、财务）；只读 Mapper，不受数据权限限制 */
@Service("mfgProductionQueryService")
public class ProductionQueryService implements ProductionQueryApi {

    static final List<String> WIP = List.of(ProdStatus.RELEASED.name(), ProdStatus.IN_PROGRESS.name(), ProdStatus.SUSPENDED.name());

    private final MfgProdOrderMapper orderMapper;
    private final MfgProdOrderMaterialMapper materialMapper;

    private final MfgProdOrderOperationMapper operationMapper;

    public ProductionQueryService(MfgProdOrderMapper orderMapper, MfgProdOrderMaterialMapper materialMapper, MfgProdOrderOperationMapper operationMapper) {
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.operationMapper = operationMapper;
    }

    static final List<String> OPEN = List.of(ProdStatus.PLANNED.name(), ProdStatus.RELEASED.name(), ProdStatus.IN_PROGRESS.name(),
            ProdStatus.SUSPENDED.name());

    @Override
    public List<OpenOrderDTO> getOpenOrders(Collection<Long> materialIds) {
        // 拆解订单不是产品的供给，也不产生子件需求，不参与 MRP / 排产
        List<MfgProdOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<MfgProdOrderDO>().in(MfgProdOrderDO::getProdStatus, OPEN)
                .ne(MfgProdOrderDO::getOrderType, ProdOrderService.DISASSEMBLY)
                .in(materialIds != null && !materialIds.isEmpty(), MfgProdOrderDO::getMaterialId, materialIds)
                .orderByAsc(MfgProdOrderDO::getPriority).orderByAsc(MfgProdOrderDO::getPlanStart).orderByAsc(MfgProdOrderDO::getId));
        if (orders.isEmpty()) return List.of();
        List<Long> ids = orders.stream().map(MfgProdOrderDO::getId).toList();
        Map<Long, List<MfgProdOrderMaterialDO>> mats = materialMapper.selectByParents(ids).stream()
                .collect(Collectors.groupingBy(MfgProdOrderMaterialDO::getProdOrderId));
        Map<Long, List<MfgProdOrderOperationDO>> ops = operationMapper.selectByParents(ids).stream()
                .collect(Collectors.groupingBy(MfgProdOrderOperationDO::getProdOrderId));
        List<OpenOrderDTO> out = new ArrayList<>();
        for (MfgProdOrderDO o : orders) {
            BigDecimal remain = MfgSupport.max0(o.getQty().subtract(MfgSupport.nz(o.getQualifiedStockedQty())).subtract(MfgSupport.nz(o.getScrappedQty())));
            List<OpenOrderDTO.Material> ml = mats.getOrDefault(o.getId(), List.of()).stream()
                    .map(m -> new OpenOrderDTO.Material(m.getId(), m.getComponentId(), m.getQtyPer(), m.getScrapRate(), m.getRequiredQty(),
                            m.getIssuedQty(), m.getReturnedGoodQty(), MaterialPlanner.openQty(m), m.getIssueMethod(), m.getOperationSeq())).toList();
            List<OpenOrderDTO.Operation> ol = ops.getOrDefault(o.getId(), List.of()).stream()
                    .sorted(java.util.Comparator.comparing(MfgProdOrderOperationDO::getSeq))
                    .map(x -> new OpenOrderDTO.Operation(x.getSeq(), x.getOperation(), x.getWorkCenterId(), x.getStdSetupMinutes(), x.getStdRunSeconds(),
                            Boolean.TRUE.equals(x.getIsReportPoint()), MfgSupport.nz(x.getGoodQty()).add(MfgSupport.nz(x.getScrapQty())))).toList();
            out.add(new OpenOrderDTO(o.getId(), o.getDocNo(), o.getOrderType(), o.getProdStatus(), o.getMaterialId(), o.getQty(), o.getCompletedQty(),
                    o.getScrappedQty(), o.getQualifiedStockedQty(), remain, o.getBomId(), o.getRoutingId(), o.getPlanStart(), o.getPlanEnd(),
                    o.getReleasedAt(), o.getPriority() == null ? 5 : o.getPriority(), o.getDeptId(), o.getSalesOrderLineId(), o.getSalesOrderNo(), ml, ol));
        }
        return out;
    }

    @Override
    public Map<Long, WipDTO> getWipQty(Collection<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) return Map.of();
        Map<Long, List<WipDTO.Order>> map = new LinkedHashMap<>();
        for (MfgProdOrderDO o : orderMapper.selectList(new LambdaQueryWrapper<MfgProdOrderDO>().in(MfgProdOrderDO::getMaterialId, materialIds)
                .in(MfgProdOrderDO::getProdStatus, WIP).ne(MfgProdOrderDO::getOrderType, ProdOrderService.DISASSEMBLY).orderByAsc(MfgProdOrderDO::getPlanEnd))) {
            BigDecimal remain = MfgSupport.max0(o.getQty().subtract(o.getQualifiedStockedQty()).subtract(o.getScrappedQty()));
            if (remain.signum() <= 0) continue;
            map.computeIfAbsent(o.getMaterialId(), k -> new ArrayList<>()).add(new WipDTO.Order(o.getId(), o.getDocNo(), remain, o.getPlanEnd(), o.getProdStatus()));
        }
        Map<Long, WipDTO> out = new LinkedHashMap<>();
        map.forEach((id, orders) -> out.put(id, new WipDTO(id, MfgSupport.sum(orders.stream().map(WipDTO.Order::remainingQty).toList()), orders)));
        return out;
    }

    @Override
    public List<ComponentDemandDTO> getOpenOrdersByComponent(Long componentId) {
        List<MfgProdOrderMaterialDO> mats = materialMapper.selectList(new LambdaQueryWrapper<MfgProdOrderMaterialDO>()
                .eq(MfgProdOrderMaterialDO::getComponentId, componentId));
        if (mats.isEmpty()) return List.of();
        Map<Long, MfgProdOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<MfgProdOrderDO>()
                        .in(MfgProdOrderDO::getId, mats.stream().map(MfgProdOrderMaterialDO::getProdOrderId).distinct().toList())
                        .in(MfgProdOrderDO::getProdStatus, WIP))
                .stream().collect(Collectors.toMap(MfgProdOrderDO::getId, Function.identity()));
        List<ComponentDemandDTO> out = new ArrayList<>();
        for (MfgProdOrderMaterialDO m : mats) {
            MfgProdOrderDO o = orders.get(m.getProdOrderId());
            if (o == null) continue;
            out.add(new ComponentDemandDTO(o.getId(), o.getDocNo(), o.getMaterialId(), componentId, m.getRequiredQty(), m.getIssuedQty(),
                    MaterialPlanner.openQty(m), o.getPlanStart(), o.getProdStatus()));
        }
        return out;
    }

    @Override
    public List<ProgressDTO> getProgress(Collection<Long> prodOrderIds) {
        if (prodOrderIds == null || prodOrderIds.isEmpty()) return List.of();
        return orderMapper.selectBatchIds(prodOrderIds).stream().map(ProductionQueryService::progress).toList();
    }

    @Override
    public List<ProgressDTO> getProgressBySalesOrderLines(Collection<Long> salesOrderLineIds) {
        if (salesOrderLineIds == null || salesOrderLineIds.isEmpty()) return List.of();
        return orderMapper.selectList(new LambdaQueryWrapper<MfgProdOrderDO>().in(MfgProdOrderDO::getSalesOrderLineId, salesOrderLineIds)
                .ne(MfgProdOrderDO::getProdStatus, ProdStatus.VOIDED.name())).stream().map(ProductionQueryService::progress).toList();
    }

    private static ProgressDTO progress(MfgProdOrderDO o) {
        return new ProgressDTO(o.getId(), o.getDocNo(), o.getMaterialId(), o.getQty(), o.getCompletedQty(), o.getScrappedQty(), o.getStockedQty(),
                o.getQualifiedStockedQty(), o.getProdStatus(), o.getPlanStart(), o.getPlanEnd(), o.getSalesOrderLineId());
    }

    @Override
    public boolean isBomUsed(Long bomId) {
        return orderMapper.selectCount(new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getBomId, bomId)
                .ne(MfgProdOrderDO::getProdStatus, ProdStatus.VOIDED.name())) > 0;
    }

    @Override
    public BigDecimal getAllocatedQty(Long materialId) {
        return getAllocatedQty(List.of(materialId)).getOrDefault(materialId, BigDecimal.ZERO);
    }

    @Override
    public Map<Long, BigDecimal> getAllocatedQty(Collection<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) return Map.of();
        List<Long> orderIds = orderMapper.selectList(new LambdaQueryWrapper<MfgProdOrderDO>().in(MfgProdOrderDO::getProdStatus, WIP)
                .select(MfgProdOrderDO::getId)).stream().map(MfgProdOrderDO::getId).toList();
        Map<Long, BigDecimal> map = new HashMap<>();
        if (orderIds.isEmpty()) return map;
        for (MfgProdOrderMaterialDO m : materialMapper.selectList(new LambdaQueryWrapper<MfgProdOrderMaterialDO>()
                .in(MfgProdOrderMaterialDO::getProdOrderId, orderIds).in(MfgProdOrderMaterialDO::getComponentId, materialIds))) {
            map.merge(m.getComponentId(), MaterialPlanner.openQty(m), BigDecimal::add);
        }
        return map;
    }
}
