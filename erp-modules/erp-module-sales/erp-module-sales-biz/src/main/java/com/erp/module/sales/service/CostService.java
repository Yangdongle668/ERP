package com.erp.module.sales.service;

import com.erp.module.purchase.api.price.PurchasePriceApi;
import com.erp.module.purchase.api.price.PurchasePriceDTO;
import com.erp.module.sales.api.cost.SalesCostProvider;
import com.erp.module.sales.config.SalesModuleConfig;
import com.erp.module.system.api.currency.CurrencyApi;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 单位成本与毛利（SAL-SO-R05、SAL-QT-R03）：
 * 成本 = {@link SalesCostProvider}（标准成本）→ 最新采购价（不含税，折本位币）→ 无；
 * 毛利率 = (不含税单价 × 汇率 ÷ 换算比例 − 成本) ÷ (不含税单价 × 汇率 ÷ 换算比例)；底价 = 成本 × (1 + 最低毛利率)。
 */
@Service("salCostService")
public class CostService {

    /** 单位成本及来源：STANDARD 标准成本 / PURCHASE 最新采购价 */
    public record UnitCost(BigDecimal cost, String source) {
    }

    /** 毛利计算结果；cost 为空表示无成本（margin、belowFloor 也为空/false） */
    public record Margin(BigDecimal cost, BigDecimal marginRate, boolean belowFloor) {
    }

    private final List<SalesCostProvider> providers;
    private final PurchasePriceApi purchasePriceApi;
    private final CurrencyApi currencyApi;
    private final SalSupport support;

    public CostService(List<SalesCostProvider> providers, PurchasePriceApi purchasePriceApi, CurrencyApi currencyApi, SalSupport support) {
        this.providers = providers;
        this.purchasePriceApi = purchasePriceApi;
        this.currencyApi = currencyApi;
        this.support = support;
    }

    /** 物料 ID → 单位成本（本位币、每基本单位、不含税）；没有成本的物料不返回 */
    public Map<Long, UnitCost> unitCosts(Collection<Long> materialIds) {
        Set<Long> ids = materialIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Map<Long, UnitCost> map = new HashMap<>();
        for (SalesCostProvider p : providers) {
            if (ids.isEmpty()) break;
            p.unitCosts(ids).forEach((id, c) -> {
                if (c != null && ids.remove(id)) map.put(id, new UnitCost(c, "STANDARD"));
            });
        }
        String base = currencyApi.getBaseCurrency();
        for (Long id : ids) {
            PurchasePriceDTO p = purchasePriceApi.getLatestPrice(id).orElse(null);
            if (p == null || p.price() == null) continue;
            BigDecimal price = p.price();
            if (!base.equals(p.currency())) {
                try {
                    price = price.multiply(currencyApi.getRate(p.currency(), LocalDate.now()));
                } catch (RuntimeException e) {
                    continue;
                }
            }
            map.put(id, new UnitCost(price.setScale(6, RoundingMode.HALF_UP), "PURCHASE"));
        }
        return map;
    }

    public BigDecimal minMarginRate() {
        BigDecimal pct = support.params().getDecimal(SalesModuleConfig.P_MIN_MARGIN);
        return SalSupport.nz(pct).divide(SalSupport.HUNDRED, 6, RoundingMode.HALF_UP);
    }

    /**
     * @param priceExclTax 不含税单价（原币，每 uom）
     * @param rate         汇率
     * @param basePerUom   每 1 个 uom = basePerUom 个基本单位
     * @param cost         单位成本（本位币、每基本单位），可空
     */
    public Margin margin(BigDecimal priceExclTax, BigDecimal rate, BigDecimal basePerUom, BigDecimal cost, BigDecimal minMargin) {
        if (cost == null || priceExclTax == null) return new Margin(null, null, false);
        BigDecimal per = basePerUom == null || basePerUom.signum() == 0 ? BigDecimal.ONE : basePerUom;
        BigDecimal unitRevenue = priceExclTax.multiply(rate).divide(per, 10, RoundingMode.HALF_UP);
        BigDecimal marginRate = unitRevenue.signum() == 0 ? new BigDecimal("-1")
                : unitRevenue.subtract(cost).divide(unitRevenue, 4, RoundingMode.HALF_UP);
        BigDecimal floor = cost.multiply(BigDecimal.ONE.add(minMargin));
        return new Margin(cost, marginRate, unitRevenue.compareTo(floor) < 0);
    }
}
