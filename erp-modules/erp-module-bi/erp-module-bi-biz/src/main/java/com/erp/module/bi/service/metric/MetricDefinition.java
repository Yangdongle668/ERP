package com.erp.module.bi.service.metric;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 代码注册的指标定义（需求 13-01 第 2 节）。计算逻辑只在代码中维护，页面只能修改展示名称、说明、负责人。
 *
 * <ul>
 *   <li>普通指标：来源汇总表上的聚合表达式（numerator），有 denominator 时值 = numerator ÷ denominator × factor；</li>
 *   <li>派生指标：由其他指标在同一结果行上计算（derive），如周转天数、DSO；</li>
 *   <li>特殊指标：由查询引擎专门处理（如新客户数）。</li>
 * </ul>
 *
 * @param dimColumns 可用维度 → 来源表列（维度白名单）；派生指标为依赖指标维度的交集，列为空
 * @param mode       时间聚合方式：SUM 期间累计；LAST 取每个时间桶最后一个快照日 / 期间；AVG 各快照日平均
 * @param sensitive  成本、价格、毛利额等敏感数值（AI 脱敏）
 */
public record MetricDefinition(String code, String name, String topic, Unit unit, String description, Source source, String numerator,
                               String denominator, int factor, Mode mode, Map<String, String> dimColumns, String permission, boolean sensitive,
                               List<String> deps, BiFunction<Map<String, BigDecimal>, Integer, BigDecimal> derive) {

    public enum Unit {
        AMOUNT, PERCENT, QTY, COUNT, DAYS, PRICE
    }

    public enum Mode {
        SUM, LAST, AVG
    }

    /** 来源汇总表；monthly 表的时间列是期间（yyyyMM） */
    public enum Source {
        SALES("bi_agg_sales_daily", "stat_date", false, "销售日汇总"),
        PURCHASE("bi_agg_purchase_daily", "stat_date", false, "采购日汇总"),
        PRODUCTION("bi_agg_production_daily", "stat_date", false, "生产日汇总"),
        QUALITY("bi_agg_quality_daily", "stat_date", false, "品质日汇总"),
        INV_SNAPSHOT("bi_agg_inventory_daily_snapshot", "stat_date", false, "库存日快照"),
        INV_MONTHLY("bi_agg_inventory_monthly", "period", true, "库存月流水"),
        FINANCE("bi_agg_finance_monthly", "period", true, "往来月汇总"),
        DERIVED(null, null, false, "派生"),
        SPECIAL("bi_agg_sales_daily", "stat_date", false, "销售日汇总（首单日期）");

        public final String table;
        public final String dateColumn;
        public final boolean monthly;
        public final String label;

        Source(String table, String dateColumn, boolean monthly, String label) {
            this.table = table;
            this.dateColumn = dateColumn;
            this.monthly = monthly;
            this.label = label;
        }
    }

    public boolean isDerived() {
        return source == Source.DERIVED;
    }

    public boolean isSpecial() {
        return source == Source.SPECIAL;
    }

    /** 维度编码 → 列名的有序映射构造 */
    static Map<String, String> dims(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
        return m;
    }
}
