package com.erp.module.bi.service.etl;

import com.erp.module.bi.api.fact.BiFacts.FinanceFact;
import com.erp.module.bi.api.fact.BiFacts.InventoryFact;
import com.erp.module.bi.api.fact.BiFacts.InventoryFlowFact;
import com.erp.module.bi.api.fact.BiFacts.ProductionFact;
import com.erp.module.bi.api.fact.BiFacts.PurchaseFact;
import com.erp.module.bi.api.fact.BiFacts.QualityFact;
import com.erp.module.bi.api.fact.BiFacts.SalesFact;
import com.erp.module.bi.dal.dataobject.BiAggFinanceDO;
import com.erp.module.bi.dal.dataobject.BiAggInventoryMonthlyDO;
import com.erp.module.bi.dal.dataobject.BiAggInventorySnapshotDO;
import com.erp.module.bi.dal.dataobject.BiAggProductionDO;
import com.erp.module.bi.dal.dataobject.BiAggPurchaseDO;
import com.erp.module.bi.dal.dataobject.BiAggQualityDO;
import com.erp.module.bi.dal.dataobject.BiAggSalesDO;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.purchase.api.supplier.SupplierDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 把各业务模块提供的事实合并为汇总表行（同一粒度键相加），并补齐类别、国家、业务员 / 采购员、部门、公司等分析维度。
 * 金额保留 2 位、数量 4 位（与表结构一致，便于全量校对时逐行比较）。
 */
final class BiAggBuilder {

    static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMM");

    private final BiLookup lookup;
    /** 物料 + 期间 → 单位成本（成本计算完成后才有） */
    private final BiFunction<Long, String, Optional<BigDecimal>> unitCost;

    BiAggBuilder(BiLookup lookup, BiFunction<Long, String, Optional<BigDecimal>> unitCost) {
        this.lookup = lookup;
        this.unitCost = unitCost;
    }

    static String period(LocalDate d) {
        return d.format(PERIOD);
    }

    // ==================== 销售 ====================

    List<BiAggSalesDO> sales(List<SalesFact> facts) {
        Set<Long> mats = new HashSet<>();
        Set<Long> custs = new HashSet<>();
        Set<Long> owners = new HashSet<>();
        facts.forEach(f -> {
            mats.add(f.materialId());
            custs.add(f.customerId());
            owners.add(f.ownerId());
        });
        lookup.preloadMaterials(mats);
        lookup.preloadCustomers(custs);
        lookup.preloadUsers(owners);
        Map<String, BiAggSalesDO> rows = new LinkedHashMap<>();
        for (SalesFact f : facts) {
            if (f.date() == null) continue;
            Long[] od = lookup.customerOwnerDept(f.customerId(), f.ownerId(), f.deptId());
            String key = f.date() + "|" + f.customerId() + "|" + f.materialId() + "|" + od[0] + "|" + od[1];
            BiAggSalesDO r = rows.computeIfAbsent(key, k -> {
                BiAggSalesDO d = new BiAggSalesDO();
                d.setStatDate(f.date());
                d.setPeriod(period(f.date()));
                d.setCustomerId(f.customerId());
                d.setMaterialId(f.materialId());
                d.setCategoryId(lookup.categoryOf(f.materialId()));
                CustomerDTO c = lookup.customer(f.customerId());
                d.setCountry(c == null ? null : c.country());
                d.setOwnerId(od[0]);
                d.setDeptId(od[1]);
                d.setOrgId(lookup.companyOf(od[1]));
                d.setOrderAmount(BigDecimal.ZERO);
                d.setShipAmount(BigDecimal.ZERO);
                d.setShipQty(BigDecimal.ZERO);
                d.setShipCost(BigDecimal.ZERO);
                d.setCostedShipAmount(BigDecimal.ZERO);
                d.setReturnAmount(BigDecimal.ZERO);
                d.setShipLineCount(0);
                d.setOnTimeLineCount(0);
                d.setReceiptAmount(BigDecimal.ZERO);
                return d;
            });
            r.setOrderAmount(r.getOrderAmount().add(nz(f.orderAmount())));
            r.setShipAmount(r.getShipAmount().add(nz(f.shipAmount())));
            r.setShipQty(r.getShipQty().add(nz(f.shipQty())));
            r.setReturnAmount(r.getReturnAmount().add(nz(f.returnAmount())));
            r.setShipLineCount(r.getShipLineCount() + f.shipLineCount());
            r.setOnTimeLineCount(r.getOnTimeLineCount() + f.onTimeLineCount());
            r.setReceiptAmount(r.getReceiptAmount().add(nz(f.receiptAmount())));
        }
        for (BiAggSalesDO r : rows.values()) {
            // 出货成本 = 出货数量 × 期间单位成本；只有已计算成本的出货额计入毛利率分母
            if (r.getMaterialId() != null && r.getShipQty().signum() != 0) {
                unitCost.apply(r.getMaterialId(), r.getPeriod()).ifPresent(u -> {
                    r.setShipCost(r.getShipQty().multiply(u));
                    r.setCostedShipAmount(r.getShipAmount());
                });
            }
            r.setOrderAmount(amt(r.getOrderAmount()));
            r.setShipAmount(amt(r.getShipAmount()));
            r.setShipQty(qty(r.getShipQty()));
            r.setShipCost(amt(r.getShipCost()));
            r.setCostedShipAmount(amt(r.getCostedShipAmount()));
            r.setReturnAmount(amt(r.getReturnAmount()));
            r.setReceiptAmount(amt(r.getReceiptAmount()));
        }
        return new ArrayList<>(rows.values());
    }

    static String salesKey(BiAggSalesDO d) {
        return d.getStatDate() + "|" + d.getCustomerId() + "|" + d.getMaterialId() + "|" + d.getOwnerId() + "|" + d.getDeptId();
    }

    static String salesValues(BiAggSalesDO d) {
        return sig(d.getCategoryId(), d.getCountry(), d.getOrgId(), d.getOrderAmount(), d.getShipAmount(), d.getShipQty(), d.getShipCost(),
                d.getCostedShipAmount(), d.getReturnAmount(), d.getShipLineCount(), d.getOnTimeLineCount(), d.getReceiptAmount());
    }

    // ==================== 采购 ====================

    List<BiAggPurchaseDO> purchase(List<PurchaseFact> facts) {
        Set<Long> mats = new HashSet<>();
        facts.forEach(f -> mats.add(f.materialId()));
        lookup.preloadMaterials(mats);
        Map<String, BiAggPurchaseDO> rows = new LinkedHashMap<>();
        for (PurchaseFact f : facts) {
            if (f.date() == null) continue;
            Long buyer = f.buyerId();
            if (buyer == null) {
                SupplierDTO s = lookup.supplier(f.supplierId());
                buyer = s == null ? null : s.buyerId();
            }
            Long dept = f.deptId() != null ? f.deptId() : lookup.deptOfUser(buyer);
            Long owner = buyer;
            String key = f.date() + "|" + f.supplierId() + "|" + f.materialId() + "|" + owner + "|" + dept;
            BiAggPurchaseDO r = rows.computeIfAbsent(key, k -> {
                BiAggPurchaseDO d = new BiAggPurchaseDO();
                d.setStatDate(f.date());
                d.setPeriod(period(f.date()));
                d.setSupplierId(f.supplierId());
                d.setMaterialId(f.materialId());
                d.setCategoryId(lookup.categoryOf(f.materialId()));
                d.setOwnerId(owner);
                d.setDeptId(dept);
                d.setOrgId(lookup.companyOf(dept));
                d.setOrderAmount(BigDecimal.ZERO);
                d.setOrderQty(BigDecimal.ZERO);
                d.setReceiptAmount(BigDecimal.ZERO);
                d.setReceiptQty(BigDecimal.ZERO);
                d.setDueLineCount(0);
                d.setOnTimeLineCount(0);
                return d;
            });
            r.setOrderAmount(r.getOrderAmount().add(nz(f.orderAmount())));
            r.setOrderQty(r.getOrderQty().add(nz(f.orderQty())));
            r.setReceiptAmount(r.getReceiptAmount().add(nz(f.receiptAmount())));
            r.setReceiptQty(r.getReceiptQty().add(nz(f.receiptQty())));
            r.setDueLineCount(r.getDueLineCount() + f.dueLineCount());
            r.setOnTimeLineCount(r.getOnTimeLineCount() + f.onTimeLineCount());
        }
        rows.values().forEach(r -> {
            r.setOrderAmount(amt(r.getOrderAmount()));
            r.setOrderQty(qty(r.getOrderQty()));
            r.setReceiptAmount(amt(r.getReceiptAmount()));
            r.setReceiptQty(qty(r.getReceiptQty()));
        });
        return new ArrayList<>(rows.values());
    }

    static String purchaseKey(BiAggPurchaseDO d) {
        return d.getStatDate() + "|" + d.getSupplierId() + "|" + d.getMaterialId() + "|" + d.getOwnerId() + "|" + d.getDeptId();
    }

    static String purchaseValues(BiAggPurchaseDO d) {
        return sig(d.getCategoryId(), d.getOrgId(), d.getOrderAmount(), d.getOrderQty(), d.getReceiptAmount(), d.getReceiptQty(), d.getDueLineCount(),
                d.getOnTimeLineCount());
    }

    // ==================== 生产 ====================

    List<BiAggProductionDO> production(List<ProductionFact> facts) {
        Set<Long> mats = new HashSet<>();
        facts.forEach(f -> mats.add(f.materialId()));
        lookup.preloadMaterials(mats);
        Map<String, BiAggProductionDO> rows = new LinkedHashMap<>();
        for (ProductionFact f : facts) {
            if (f.date() == null) continue;
            BiAggProductionDO r = rows.computeIfAbsent(f.date() + "|" + f.deptId() + "|" + f.materialId(), k -> {
                BiAggProductionDO d = new BiAggProductionDO();
                d.setStatDate(f.date());
                d.setPeriod(period(f.date()));
                d.setDeptId(f.deptId());
                d.setMaterialId(f.materialId());
                d.setCategoryId(lookup.categoryOf(f.materialId()));
                d.setPlanQty(BigDecimal.ZERO);
                d.setGoodQty(BigDecimal.ZERO);
                d.setDefectQty(BigDecimal.ZERO);
                d.setScrapQty(BigDecimal.ZERO);
                d.setWorkHours(BigDecimal.ZERO);
                d.setStdHours(BigDecimal.ZERO);
                d.setInQty(BigDecimal.ZERO);
                d.setFirstPassQty(BigDecimal.ZERO);
                d.setDelayedOrderCount(0);
                return d;
            });
            r.setPlanQty(r.getPlanQty().add(nz(f.planQty())));
            r.setGoodQty(r.getGoodQty().add(nz(f.goodQty())));
            r.setDefectQty(r.getDefectQty().add(nz(f.defectQty())));
            r.setScrapQty(r.getScrapQty().add(nz(f.scrapQty())));
            r.setWorkHours(r.getWorkHours().add(nz(f.workHours())));
            r.setStdHours(r.getStdHours().add(nz(f.stdHours())));
            r.setInQty(r.getInQty().add(nz(f.inQty())));
            r.setFirstPassQty(r.getFirstPassQty().add(nz(f.firstPassQty())));
            r.setDelayedOrderCount(r.getDelayedOrderCount() + f.delayedOrderCount());
        }
        rows.values().forEach(r -> {
            r.setPlanQty(qty(r.getPlanQty()));
            r.setGoodQty(qty(r.getGoodQty()));
            r.setDefectQty(qty(r.getDefectQty()));
            r.setScrapQty(qty(r.getScrapQty()));
            r.setWorkHours(qty(r.getWorkHours()));
            r.setStdHours(qty(r.getStdHours()));
            r.setInQty(qty(r.getInQty()));
            r.setFirstPassQty(qty(r.getFirstPassQty()));
        });
        return new ArrayList<>(rows.values());
    }

    static String productionKey(BiAggProductionDO d) {
        return d.getStatDate() + "|" + d.getDeptId() + "|" + d.getMaterialId();
    }

    static String productionValues(BiAggProductionDO d) {
        return sig(d.getCategoryId(), d.getPlanQty(), d.getGoodQty(), d.getDefectQty(), d.getScrapQty(), d.getWorkHours(), d.getStdHours(), d.getInQty(),
                d.getFirstPassQty(), d.getDelayedOrderCount());
    }

    // ==================== 品质 ====================

    List<BiAggQualityDO> quality(List<QualityFact> facts) {
        Set<Long> mats = new HashSet<>();
        facts.forEach(f -> mats.add(f.materialId()));
        lookup.preloadMaterials(mats);
        Map<String, BiAggQualityDO> rows = new LinkedHashMap<>();
        for (QualityFact f : facts) {
            if (f.date() == null || f.inspectType() == null) continue;
            String key = f.date() + "|" + f.inspectType() + "|" + f.supplierId() + "|" + f.customerId() + "|" + f.materialId();
            BiAggQualityDO r = rows.computeIfAbsent(key, k -> {
                BiAggQualityDO d = new BiAggQualityDO();
                d.setStatDate(f.date());
                d.setPeriod(period(f.date()));
                d.setInspectType(f.inspectType());
                d.setSupplierId(f.supplierId());
                d.setCustomerId(f.customerId());
                d.setMaterialId(f.materialId());
                d.setCategoryId(lookup.categoryOf(f.materialId()));
                d.setLotCount(0);
                d.setPassCount(0);
                d.setConcessionCount(0);
                d.setRejectCount(0);
                d.setDefectCount(0);
                d.setNcrCount(0);
                d.setComplaintCount(0);
                return d;
            });
            r.setLotCount(r.getLotCount() + f.lotCount());
            r.setPassCount(r.getPassCount() + f.passCount());
            r.setConcessionCount(r.getConcessionCount() + f.concessionCount());
            r.setRejectCount(r.getRejectCount() + f.rejectCount());
            r.setDefectCount(r.getDefectCount() + f.defectCount());
            r.setNcrCount(r.getNcrCount() + f.ncrCount());
            r.setComplaintCount(r.getComplaintCount() + f.complaintCount());
        }
        return new ArrayList<>(rows.values());
    }

    static String qualityKey(BiAggQualityDO d) {
        return d.getStatDate() + "|" + d.getInspectType() + "|" + d.getSupplierId() + "|" + d.getCustomerId() + "|" + d.getMaterialId();
    }

    static String qualityValues(BiAggQualityDO d) {
        return sig(d.getCategoryId(), d.getLotCount(), d.getPassCount(), d.getConcessionCount(), d.getRejectCount(), d.getDefectCount(), d.getNcrCount(),
                d.getComplaintCount());
    }

    // ==================== 库存 ====================

    List<BiAggInventorySnapshotDO> snapshot(LocalDate date, List<InventoryFact> facts) {
        Set<Long> mats = new HashSet<>();
        facts.forEach(f -> mats.add(f.materialId()));
        lookup.preloadMaterials(mats);
        Map<String, BiAggInventorySnapshotDO> rows = new LinkedHashMap<>();
        for (InventoryFact f : facts) {
            BiAggInventorySnapshotDO r = rows.computeIfAbsent(f.warehouseId() + "|" + f.materialId(), k -> {
                BiAggInventorySnapshotDO d = new BiAggInventorySnapshotDO();
                d.setStatDate(date);
                d.setPeriod(period(date));
                d.setWarehouseId(f.warehouseId());
                d.setWarehouseType(f.warehouseType());
                d.setMaterialId(f.materialId());
                d.setCategoryId(lookup.categoryOf(f.materialId()));
                d.setQty(BigDecimal.ZERO);
                d.setAmount(BigDecimal.ZERO);
                return d;
            });
            r.setQty(r.getQty().add(nz(f.qty())));
            r.setAmount(r.getAmount().add(nz(f.amount())));
            r.setLastOutDate(max(r.getLastOutDate(), f.lastOutDate()));
            r.setLastInDate(max(r.getLastInDate(), f.lastInDate()));
        }
        rows.values().forEach(r -> {
            r.setQty(qty(r.getQty()));
            r.setAmount(amt(r.getAmount()));
            LocalDate lastMove = r.getLastOutDate() != null ? r.getLastOutDate() : r.getLastInDate();
            r.setIdleDays(lastMove == null ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(lastMove, date));
            r.setAgeDays(r.getLastInDate() == null ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(r.getLastInDate(), date));
        });
        return new ArrayList<>(rows.values());
    }

    static String snapshotKey(BiAggInventorySnapshotDO d) {
        return d.getStatDate() + "|" + d.getWarehouseId() + "|" + d.getMaterialId();
    }

    static String snapshotValues(BiAggInventorySnapshotDO d) {
        return sig(d.getWarehouseType(), d.getCategoryId(), d.getQty(), d.getAmount(), d.getLastOutDate(), d.getLastInDate(), d.getIdleDays(),
                d.getAgeDays());
    }

    List<BiAggInventoryMonthlyDO> inventoryMonthly(List<InventoryFlowFact> facts) {
        Set<Long> mats = new HashSet<>();
        facts.forEach(f -> mats.add(f.materialId()));
        lookup.preloadMaterials(mats);
        Map<String, BiAggInventoryMonthlyDO> rows = new LinkedHashMap<>();
        for (InventoryFlowFact f : facts) {
            if (f.period() == null) continue;
            BiAggInventoryMonthlyDO r = rows.computeIfAbsent(f.period() + "|" + f.warehouseType() + "|" + f.materialId(), k -> {
                BiAggInventoryMonthlyDO d = new BiAggInventoryMonthlyDO();
                d.setPeriod(f.period());
                d.setWarehouseType(f.warehouseType());
                d.setMaterialId(f.materialId());
                d.setCategoryId(lookup.categoryOf(f.materialId()));
                d.setInAmount(BigDecimal.ZERO);
                d.setOutAmount(BigDecimal.ZERO);
                return d;
            });
            r.setInAmount(r.getInAmount().add(nz(f.inAmount())));
            r.setOutAmount(r.getOutAmount().add(nz(f.outAmount())));
        }
        rows.values().forEach(r -> {
            r.setInAmount(amt(r.getInAmount()));
            r.setOutAmount(amt(r.getOutAmount()));
        });
        return new ArrayList<>(rows.values());
    }

    static String monthlyKey(BiAggInventoryMonthlyDO d) {
        return d.getPeriod() + "|" + d.getWarehouseType() + "|" + d.getMaterialId();
    }

    static String monthlyValues(BiAggInventoryMonthlyDO d) {
        return sig(d.getCategoryId(), d.getInAmount(), d.getOutAmount());
    }

    // ==================== 往来 ====================

    List<BiAggFinanceDO> finance(List<FinanceFact> facts) {
        Set<Long> custs = new HashSet<>();
        facts.forEach(f -> {
            if ("CUSTOMER".equals(f.partnerType())) custs.add(f.partnerId());
        });
        lookup.preloadCustomers(custs);
        Map<String, BiAggFinanceDO> rows = new LinkedHashMap<>();
        for (FinanceFact f : facts) {
            if (f.period() == null || f.partnerType() == null) continue;
            BiAggFinanceDO r = rows.computeIfAbsent(f.period() + "|" + f.partnerType() + "|" + f.partnerId(), k -> {
                BiAggFinanceDO d = new BiAggFinanceDO();
                d.setPeriod(f.period());
                d.setPartnerType(f.partnerType());
                d.setPartnerId(f.partnerId());
                Long owner;
                Long dept;
                if ("CUSTOMER".equals(f.partnerType())) {
                    Long[] od = lookup.customerOwnerDept(f.partnerId(), null, null);
                    owner = od[0];
                    dept = od[1];
                } else {
                    SupplierDTO s = lookup.supplier(f.partnerId());
                    owner = s == null ? null : s.buyerId();
                    dept = lookup.deptOfUser(owner);
                }
                d.setOwnerId(owner);
                d.setDeptId(dept);
                d.setOrgId(lookup.companyOf(dept));
                d.setBeginBalance(BigDecimal.ZERO);
                d.setAddAmount(BigDecimal.ZERO);
                d.setSettleAmount(BigDecimal.ZERO);
                d.setEndBalance(BigDecimal.ZERO);
                d.setOverdueAmount(BigDecimal.ZERO);
                return d;
            });
            r.setBeginBalance(r.getBeginBalance().add(nz(f.beginBalance())));
            r.setAddAmount(r.getAddAmount().add(nz(f.addAmount())));
            r.setSettleAmount(r.getSettleAmount().add(nz(f.settleAmount())));
            r.setEndBalance(r.getEndBalance().add(nz(f.endBalance())));
            r.setOverdueAmount(r.getOverdueAmount().add(nz(f.overdueAmount())));
        }
        rows.values().forEach(r -> {
            r.setBeginBalance(amt(r.getBeginBalance()));
            r.setAddAmount(amt(r.getAddAmount()));
            r.setSettleAmount(amt(r.getSettleAmount()));
            r.setEndBalance(amt(r.getEndBalance()));
            r.setOverdueAmount(amt(r.getOverdueAmount()));
        });
        return new ArrayList<>(rows.values());
    }

    static String financeKey(BiAggFinanceDO d) {
        return d.getPeriod() + "|" + d.getPartnerType() + "|" + d.getPartnerId();
    }

    static String financeValues(BiAggFinanceDO d) {
        return sig(d.getOwnerId(), d.getDeptId(), d.getOrgId(), d.getBeginBalance(), d.getAddAmount(), d.getSettleAmount(), d.getEndBalance(),
                d.getOverdueAmount());
    }

    // ==================== 工具 ====================

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static BigDecimal amt(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal qty(BigDecimal v) {
        return nz(v).setScale(4, RoundingMode.HALF_UP);
    }

    static LocalDate max(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    /** 值签名：数值去掉末尾 0 后比较，空数值按 0 */
    static String sig(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (Object v : values) {
            if (v instanceof BigDecimal d) sb.append(d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString());
            else sb.append(v);
            sb.append('|');
        }
        return sb.toString();
    }
}
