package com.erp.module.pmc.service;

import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.bom.BomApi;
import com.erp.module.engineering.api.routing.RoutingApi;
import com.erp.module.engineering.api.routing.WorkCenterApi;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.inventory.api.stock.InventoryQueryApi;
import com.erp.module.inventory.api.stock.StockSummary;
import com.erp.module.production.api.order.ProductionQueryApi;
import com.erp.module.production.api.order.WipDTO;
import com.erp.module.purchase.api.order.InTransitDTO;
import com.erp.module.purchase.api.order.PurchaseQueryApi;
import com.erp.module.purchase.api.supplier.SupplierApi;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 计划用到的外部数据（库存、在途、在制、BOM、工艺、客户、供应商），统一批量读取 */
@Component("pmcPlanningData")
public class PlanningData {

    /** 一笔在途供应（采购 / 委外 / 待检） */
    public record Supply(String docType, Long docId, String docNo, Long lineId, BigDecimal qty, LocalDate date, Long supplierId) {
    }

    private final InventoryQueryApi inventoryQueryApi;
    private final PurchaseQueryApi purchaseQueryApi;
    private final ProductionQueryApi productionQueryApi;
    private final BomApi bomApi;
    private final RoutingApi routingApi;
    private final WorkCenterApi workCenterApi;
    private final CustomerApi customerApi;
    private final SupplierApi supplierApi;

    public PlanningData(InventoryQueryApi inventoryQueryApi, PurchaseQueryApi purchaseQueryApi, ProductionQueryApi productionQueryApi, BomApi bomApi,
                        RoutingApi routingApi, WorkCenterApi workCenterApi, CustomerApi customerApi, SupplierApi supplierApi) {
        this.inventoryQueryApi = inventoryQueryApi;
        this.purchaseQueryApi = purchaseQueryApi;
        this.productionQueryApi = productionQueryApi;
        this.bomApi = bomApi;
        this.routingApi = routingApi;
        this.workCenterApi = workCenterApi;
        this.customerApi = customerApi;
        this.supplierApi = supplierApi;
    }

    public BomApi bomApi() {
        return bomApi;
    }

    public RoutingApi routingApi() {
        return routingApi;
    }

    public ProductionQueryApi productionQueryApi() {
        return productionQueryApi;
    }

    public SupplierApi supplierApi() {
        return supplierApi;
    }

    public Map<Long, StockSummary> stock(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : inventoryQueryApi.getStockSummary(set);
    }

    /** 可用量（可用仓合格未冻结 − 预留） */
    public Map<Long, BigDecimal> available(Collection<Long> ids) {
        Map<Long, BigDecimal> map = new HashMap<>();
        stock(ids).forEach((k, v) -> map.put(k, PmcSupport.max0(v.availableQty())));
        return map;
    }

    public Map<Long, InTransitDTO> inTransit(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : purchaseQueryApi.getInTransitQty(set);
    }

    /**
     * 在途供应明细（按日期）：采购 / 委外未到货（日期取确认交期，没有时取要求日期）+ 待检仓数量（日期 = 今天 + 1）。
     */
    public Map<Long, List<Supply>> supplies(Collection<Long> ids) {
        Map<Long, List<Supply>> map = new HashMap<>();
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        inTransit(ids).forEach((id, t) -> {
            List<Supply> list = map.computeIfAbsent(id, k -> new ArrayList<>());
            for (InTransitDTO.Detail d : t.details()) {
                if (d.qty() == null || d.qty().signum() <= 0) continue;
                list.add(new Supply(d.docType(), d.docId(), d.docNo(), d.lineId(), d.qty(), d.expectedDate() == null ? tomorrow : d.expectedDate(),
                        d.supplierId()));
            }
        });
        stock(ids).forEach((id, s) -> {
            if (s.qcQty() != null && s.qcQty().signum() > 0) {
                map.computeIfAbsent(id, k -> new ArrayList<>()).add(new Supply("QC", null, "待检", null, s.qcQty(), tomorrow, null));
            }
        });
        map.values().forEach(l -> l.sort(Comparator.comparing(Supply::date)));
        return map;
    }

    public Map<Long, BigDecimal> wip(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Map.of();
        Map<Long, BigDecimal> map = new HashMap<>();
        productionQueryApi.getWipQty(set).forEach((k, v) -> map.put(k, v.wipQty()));
        return map;
    }

    public Map<Long, WipDTO> wipDetail(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : productionQueryApi.getWipQty(set);
    }

    public Map<Long, CustomerDTO> customers(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : customerApi.getCustomers(set);
    }

    public static String customerName(Map<Long, CustomerDTO> cs, Long id) {
        CustomerDTO c = id == null ? null : cs.get(id);
        return c == null ? null : c.shortName() != null && !c.shortName().isBlank() ? c.shortName() : c.name();
    }

    public Map<Long, SupplierDTO> suppliers(Collection<Long> ids) {
        Map<Long, SupplierDTO> map = new HashMap<>();
        for (Long id : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet())) supplierApi.getSupplier(id).ifPresent(s -> map.put(id, s));
        return map;
    }

    public Map<Long, WorkCenterDTO> workCenters() {
        Map<Long, WorkCenterDTO> map = new HashMap<>();
        for (WorkCenterDTO w : workCenterApi.list()) map.put(w.id(), w);
        return map;
    }
}
