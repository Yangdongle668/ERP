package com.erp.module.inventory.service.bi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.bi.api.fact.BiFactProvider;
import com.erp.module.bi.api.fact.BiFacts.InventoryFact;
import com.erp.module.bi.api.fact.BiFacts.InventoryFlowFact;
import com.erp.module.inventory.dal.dataobject.PeriodBalanceDO;
import com.erp.module.inventory.dal.dataobject.StockDO;
import com.erp.module.inventory.dal.dataobject.StockTxnDO;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.mapper.PeriodBalanceMapper;
import com.erp.module.inventory.dal.mapper.StockMapper;
import com.erp.module.inventory.dal.mapper.StockTxnMapper;
import com.erp.module.inventory.dal.mapper.WarehouseMapper;
import com.erp.module.inventory.service.posting.PostingModels;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BI 库存事实（需求 13-01）：当前库存（仓库 × 物料）的数量与参考金额；参考单价 = 最近一期月结加权单价，
 * 没有时取最近一笔有单价的入库流水单价。库存流水按期间、仓库类型汇总入库 / 出库金额（不含调拨）。
 */
@Component
public class InventoryBiFactProvider implements BiFactProvider {

    private final StockMapper stockMapper;
    private final StockTxnMapper txnMapper;
    private final PeriodBalanceMapper balanceMapper;
    private final WarehouseMapper warehouseMapper;

    public InventoryBiFactProvider(StockMapper stockMapper, StockTxnMapper txnMapper, PeriodBalanceMapper balanceMapper, WarehouseMapper warehouseMapper) {
        this.stockMapper = stockMapper;
        this.txnMapper = txnMapper;
        this.balanceMapper = balanceMapper;
        this.warehouseMapper = warehouseMapper;
    }

    private Map<Long, WarehouseDO> warehouses() {
        return warehouseMapper.selectList(null).stream().collect(Collectors.toMap(WarehouseDO::getId, Function.identity()));
    }

    @Override
    public List<InventoryFact> inventorySnapshot() {
        Map<Long, WarehouseDO> whs = warehouses();
        Map<String, BigDecimal[]> qty = new LinkedHashMap<>();
        Map<String, LocalDate[]> dates = new HashMap<>();
        for (StockDO s : stockMapper.selectList(new LambdaQueryWrapper<StockDO>().ne(StockDO::getQty, BigDecimal.ZERO))) {
            String key = s.getWarehouseId() + ":" + s.getMaterialId();
            qty.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO})[0] = qty.get(key)[0].add(s.getQty());
            LocalDate[] d = dates.computeIfAbsent(key, k -> new LocalDate[2]);
            d[0] = max(d[0], s.getLastOutDate());
            d[1] = max(d[1], s.getLastInDate());
        }
        Map<Long, BigDecimal> prices = prices();
        List<InventoryFact> facts = new ArrayList<>();
        qty.forEach((key, q) -> {
            String[] p = key.split(":");
            Long wid = Long.valueOf(p[0]);
            Long mid = Long.valueOf(p[1]);
            WarehouseDO w = whs.get(wid);
            BigDecimal price = prices.getOrDefault(mid, BigDecimal.ZERO);
            facts.add(new InventoryFact(wid, w == null || w.getWarehouseType() == null ? null : w.getWarehouseType().name(), mid, q[0],
                    q[0].multiply(price).setScale(2, RoundingMode.HALF_UP), dates.get(key)[0], dates.get(key)[1]));
        });
        return facts;
    }

    /** 物料参考单价 */
    private Map<Long, BigDecimal> prices() {
        Map<Long, BigDecimal> prices = new HashMap<>();
        for (StockTxnDO t : txnMapper.selectList(new LambdaQueryWrapper<StockTxnDO>().eq(StockTxnDO::getDirection, "IN").isNotNull(StockTxnDO::getUnitCost)
                .gt(StockTxnDO::getUnitCost, BigDecimal.ZERO).select(StockTxnDO::getMaterialId, StockTxnDO::getUnitCost).orderByAsc(StockTxnDO::getId))) {
            prices.put(t.getMaterialId(), t.getUnitCost());
        }
        Map<Long, String> latest = new HashMap<>();
        for (PeriodBalanceDO b : balanceMapper.selectList(new LambdaQueryWrapper<PeriodBalanceDO>().isNotNull(PeriodBalanceDO::getAvgCost)
                .gt(PeriodBalanceDO::getAvgCost, BigDecimal.ZERO).orderByAsc(PeriodBalanceDO::getPeriod))) {
            if (latest.getOrDefault(b.getMaterialId(), "").compareTo(b.getPeriod()) <= 0) {
                latest.put(b.getMaterialId(), b.getPeriod());
                prices.put(b.getMaterialId(), b.getAvgCost());
            }
        }
        return prices;
    }

    @Override
    public List<InventoryFlowFact> inventoryFlows(String fromPeriod, String toPeriod) {
        Map<Long, WarehouseDO> whs = warehouses();
        Map<String, BigDecimal[]> sums = new LinkedHashMap<>();
        for (StockTxnDO t : txnMapper.selectList(new LambdaQueryWrapper<StockTxnDO>().ge(StockTxnDO::getPeriod, fromPeriod).le(StockTxnDO::getPeriod, toPeriod)
                .ne(StockTxnDO::getDocType, PostingModels.DOC_TRANSFER).isNotNull(StockTxnDO::getAmount)
                .select(StockTxnDO::getPeriod, StockTxnDO::getWarehouseId, StockTxnDO::getMaterialId, StockTxnDO::getDirection, StockTxnDO::getAmount))) {
            if (!t.getPeriod().matches("\\d{6}")) continue;
            WarehouseDO w = whs.get(t.getWarehouseId());
            String type = w == null || w.getWarehouseType() == null ? "" : w.getWarehouseType().name();
            BigDecimal[] s = sums.computeIfAbsent(t.getPeriod() + ":" + type + ":" + t.getMaterialId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if ("IN".equals(t.getDirection())) s[0] = s[0].add(t.getAmount());
            else s[1] = s[1].add(t.getAmount());
        }
        List<InventoryFlowFact> facts = new ArrayList<>();
        sums.forEach((k, s) -> {
            String[] p = k.split(":", -1);
            facts.add(new InventoryFlowFact(p[0], p[1].isEmpty() ? null : p[1], Long.valueOf(p[2]), s[0], s[1]));
        });
        return facts;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a == null ? b : b == null ? a : a.isAfter(b) ? a : b;
    }
}
