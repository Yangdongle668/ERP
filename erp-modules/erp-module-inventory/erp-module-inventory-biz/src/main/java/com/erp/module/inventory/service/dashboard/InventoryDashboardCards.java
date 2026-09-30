package com.erp.module.inventory.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.inventory.dal.dataobject.StockInDO;
import com.erp.module.inventory.dal.dataobject.StockOutDO;
import com.erp.module.inventory.dal.dataobject.StockTxnDO;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.mapper.StockInMapper;
import com.erp.module.inventory.dal.mapper.StockOutMapper;
import com.erp.module.inventory.dal.mapper.StockTxnMapper;
import com.erp.module.inventory.dal.mapper.WarehouseMapper;
import com.erp.module.workbench.api.dashboard.CardData;
import com.erp.module.workbench.api.dashboard.DashboardCard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 工作台看板卡片（需求 02-01 第 3 节）：待入库、待出库（待确认单据数）、库存金额（按仓库类型，流水金额结存） */
@Configuration
public class InventoryDashboardCards {

    private final StockInMapper inMapper;
    private final StockOutMapper outMapper;
    private final StockTxnMapper txnMapper;
    private final WarehouseMapper warehouseMapper;

    public InventoryDashboardCards(StockInMapper inMapper, StockOutMapper outMapper, StockTxnMapper txnMapper, WarehouseMapper warehouseMapper) {
        this.inMapper = inMapper;
        this.outMapper = outMapper;
        this.txnMapper = txnMapper;
        this.warehouseMapper = warehouseMapper;
    }

    @Bean
    public DashboardCard invInPendingCard() {
        return DashboardCard.of("INV_IN_PENDING", "待入库", "inv:in:query", 140, "/inventory/in", () ->
                CardData.count(inMapper.selectCount(new LambdaQueryWrapper<StockInDO>().eq(StockInDO::getStatus, DocStatus.DRAFT)), "待确认入库单"));
    }

    @Bean
    public DashboardCard invOutPendingCard() {
        return DashboardCard.of("INV_OUT_PENDING", "待出库", "inv:out:query", 150, "/inventory/out", () ->
                CardData.count(outMapper.selectCount(new LambdaQueryWrapper<StockOutDO>().eq(StockOutDO::getStatus, DocStatus.DRAFT)), "待确认出库单"));
    }

    @Bean
    public DashboardCard invStockAmountCard() {
        return DashboardCard.of("INV_STOCK_AMOUNT", "库存金额", "inv:stock:cost", 160, "/inventory/analysis", () -> {
            Map<Long, WarehouseDO> whs = warehouseMapper.selectList(null).stream().collect(Collectors.toMap(WarehouseDO::getId, Function.identity()));
            Map<String, BigDecimal> byType = new LinkedHashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            List<Map<String, Object>> rows = txnMapper.selectMaps(new QueryWrapper<StockTxnDO>().select("warehouse_id", "direction", "SUM(amount) AS amt")
                    .isNotNull("amount").groupBy("warehouse_id", "direction"));
            for (Map<String, Object> r : rows) {
                Object amt = r.get("amt") != null ? r.get("amt") : r.get("AMT");
                Object wid = r.get("warehouse_id") != null ? r.get("warehouse_id") : r.get("WAREHOUSE_ID");
                Object dir = r.get("direction") != null ? r.get("direction") : r.get("DIRECTION");
                if (amt == null || wid == null) continue;
                BigDecimal v = new BigDecimal(amt.toString());
                if ("OUT".equals(String.valueOf(dir))) v = v.negate();
                WarehouseDO w = whs.get(Long.valueOf(wid.toString()));
                String type = w == null || w.getWarehouseType() == null ? "其他" : w.getWarehouseType().label();
                byType.merge(type, v, BigDecimal::add);
                total = total.add(v);
            }
            String sub = byType.entrySet().stream().filter(e -> e.getValue().signum() != 0)
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(3)
                    .map(e -> e.getKey() + " " + e.getValue().setScale(0, RoundingMode.HALF_UP).toPlainString()).collect(Collectors.joining("，"));
            return CardData.amount(total.setScale(2, RoundingMode.HALF_UP), sub.isEmpty() ? null : sub);
        });
    }
}
