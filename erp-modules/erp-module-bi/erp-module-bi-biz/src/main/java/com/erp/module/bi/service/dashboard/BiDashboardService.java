package com.erp.module.bi.service.dashboard;

import com.erp.common.exception.BizException;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.bi.api.BiErrorCodes;
import com.erp.module.bi.config.BiModuleConfig;
import com.erp.module.bi.service.metric.BiMetricService;
import com.erp.module.bi.service.metric.MetricRegistry;
import com.erp.module.bi.service.query.BiQuery;
import com.erp.module.bi.service.query.BiQueryResult;
import com.erp.module.bi.service.query.BiQueryService;
import com.erp.module.sales.api.order.OpenLineFilter;
import com.erp.module.sales.api.order.SalesOrderHeaderDTO;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.param.ParamApi;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 经营驾驶舱（需求 13-02）：一次返回全部卡片数据。无 {@code bi:finance:view} 时不返回毛利、应收（BI-DSH-R02）；
 * 所有数字按当前用户数据范围过滤。
 */
@Service
public class BiDashboardService {

    public enum Period { THIS_MONTH, LAST_MONTH, THIS_QUARTER, THIS_YEAR, CUSTOM }

    public enum Compare { MOM, YOY }

    /** KPI：value 本期、compareValue 对比期；changePct 变化率（%）；比率类指标用 changePt（百分点）；extra 附属值（毛利率、逾期应收） */
    public record Kpi(String code, String name, String unit, BigDecimal value, BigDecimal compareValue, BigDecimal changePct, BigDecimal changePt,
                      String extraName, String extraUnit, BigDecimal extra, String route, BigDecimal target, BigDecimal attainmentPct) {

        /** 附加目标与达成率（%，保留 1 位） */
        Kpi withTarget(BigDecimal target) {
            if (target == null || target.signum() <= 0) return this;
            BigDecimal pct = value == null ? BigDecimal.ZERO : value.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP);
            return new Kpi(code, name, unit, value, compareValue, changePct, changePt, extraName, extraUnit, extra, route, target, pct);
        }
    }

    public record TrendPoint(String month, BigDecimal order, BigDecimal ship, BigDecimal receipt) {
    }

    public record Share(String id, String label, BigDecimal value, BigDecimal share) {
    }

    public record Delivery(BigDecimal onTimeRate, BigDecimal openOrderAmount, int overdueLines) {
    }

    public record QualityPoint(String month, BigDecimal iqcPassRate, BigDecimal fpy, BigDecimal complaints) {
    }

    public record InventoryPart(String type, String label, BigDecimal amount) {
    }

    public record Dashboard(LocalDate from, LocalDate to, LocalDate compareFrom, LocalDate compareTo, String compare, boolean finance, List<Kpi> kpis,
                            List<TrendPoint> trend, List<Share> topCustomers, Delivery delivery, List<Share> categories, List<QualityPoint> quality,
                            List<InventoryPart> inventory, BigDecimal slowMovingAmount, LocalDateTime dataUpdatedAt) {
    }

    /** 期间范围 */
    public record Range(LocalDate from, LocalDate to, LocalDate compareFrom, LocalDate compareTo) {
    }

    private final BiQueryService queryService;
    private final BiMetricService metricService;
    private final SalesOrderQueryApi salesOrderQueryApi;
    private final OrgApi orgApi;
    private final ParamApi paramApi;
    private final BiTargetService targetService;

    public BiDashboardService(BiQueryService queryService, BiMetricService metricService, SalesOrderQueryApi salesOrderQueryApi, OrgApi orgApi,
                              ParamApi paramApi, BiTargetService targetService) {
        this.targetService = targetService;
        this.queryService = queryService;
        this.metricService = metricService;
        this.salesOrderQueryApi = salesOrderQueryApi;
        this.orgApi = orgApi;
        this.paramApi = paramApi;
    }

    /** BI-DSH-R01：本月、上月、本季、本年（财年起始月）、自定义；环比为上一个同长度期间，同比为去年同期 */
    public Range range(Period period, LocalDate from, LocalDate to, Compare compare, LocalDate today) {
        LocalDate f;
        LocalDate t;
        switch (period == null ? Period.THIS_MONTH : period) {
            case LAST_MONTH -> {
                YearMonth ym = YearMonth.from(today).minusMonths(1);
                f = ym.atDay(1);
                t = ym.atEndOfMonth();
            }
            case THIS_QUARTER -> {
                f = LocalDate.of(today.getYear(), (today.getMonthValue() - 1) / 3 * 3 + 1, 1);
                t = f.plusMonths(3).minusDays(1);
            }
            case THIS_YEAR -> {
                int start = paramApi.getInt(BiModuleConfig.P_FISCAL_START);
                f = LocalDate.of(today.getMonthValue() >= start ? today.getYear() : today.getYear() - 1, start, 1);
                t = f.plusYears(1).minusDays(1);
            }
            case CUSTOM -> {
                if (from == null || to == null || from.isAfter(to)) throw BizException.of(BiErrorCodes.QUERY_INVALID, "请选择正确的自定义期间");
                f = from;
                t = to;
            }
            default -> {
                f = today.withDayOfMonth(1);
                t = YearMonth.from(today).atEndOfMonth();
            }
        }
        LocalDate cf;
        LocalDate ct;
        if (compare == Compare.YOY) {
            cf = f.minusYears(1);
            ct = t.minusYears(1);
        } else if (period != Period.CUSTOM || isWholeMonths(f, t)) {
            long months = ChronoUnit.MONTHS.between(YearMonth.from(f), YearMonth.from(t)) + 1;
            cf = f.minusMonths(months);
            ct = YearMonth.from(t.minusMonths(months)).atEndOfMonth();
        } else {
            long days = ChronoUnit.DAYS.between(f, t) + 1;
            cf = f.minusDays(days);
            ct = t.minusDays(days);
        }
        return new Range(f, t, cf, ct);
    }

    private static boolean isWholeMonths(LocalDate f, LocalDate t) {
        return f.getDayOfMonth() == 1 && t.equals(YearMonth.from(t).atEndOfMonth());
    }

    public Dashboard dashboard(Period period, LocalDate from, LocalDate to, Compare compare) {
        LoginUser user = SecurityUtils.getLoginUser();
        boolean finance = user.hasPermission(BiModuleConfig.PERM_FINANCE);
        Compare cmp = compare == null ? Compare.MOM : compare;
        LocalDate today = LocalDate.now();
        Range r = range(period, from, to, cmp, today);
        Map<String, String> names = metricService.names();

        // KPI 行
        List<String> kpiMetrics = new ArrayList<>(List.of("sales_order_amount", "sales_ship_amount", "sales_receipt_amount", "inventory_amount",
                "on_time_delivery_rate", "slow_moving_amount"));
        if (finance) kpiMetrics.addAll(List.of("gross_profit", "gross_margin", "ar_balance", "ar_overdue"));
        Map<String, BigDecimal> cur = totals(kpiMetrics, r.from(), r.to());
        Map<String, BigDecimal> prev = totals(kpiMetrics, r.compareFrom(), r.compareTo());
        List<Kpi> kpis = new ArrayList<>();
        kpis.add(kpi("sales_order_amount", names, cur, prev, null, null, "/bi/sales"));
        kpis.add(kpi("sales_ship_amount", names, cur, prev, null, null, "/bi/sales"));
        kpis.add(kpi("sales_receipt_amount", names, cur, prev, null, null, "/bi/sales"));
        if (finance) {
            kpis.add(kpi("gross_profit", names, cur, prev, "gross_margin", cur.get("gross_margin"), "/bi/finance"));
            kpis.add(kpi("ar_balance", names, cur, prev, "ar_overdue", cur.get("ar_overdue"), "/bi/finance"));
        }
        kpis.add(kpi("inventory_amount", names, cur, prev, null, null, "/bi/inventory"));
        kpis.replaceAll(k -> k.withTarget(targetService.targetFor(k.code(), r.from(), r.to())));

        // 近 12 个月趋势
        YearMonth endMonth = YearMonth.from(r.to());
        LocalDate trendFrom = endMonth.minusMonths(11).atDay(1);
        BiQueryResult trendResult = queryService.queryInternal(BiQuery.of(List.of("sales_order_amount", "sales_ship_amount", "sales_receipt_amount"),
                List.of("date"), trendFrom, endMonth.atEndOfMonth(), "month"));
        Map<String, Map<String, Object>> byMonth = index(trendResult);
        List<TrendPoint> trend = new ArrayList<>();
        for (YearMonth ym = endMonth.minusMonths(11); !ym.isAfter(endMonth); ym = ym.plusMonths(1)) {
            Map<String, Object> row = byMonth.getOrDefault(ym.toString(), Map.of());
            trend.add(new TrendPoint(ym.toString(), dec(row.get("sales_order_amount")), dec(row.get("sales_ship_amount")),
                    dec(row.get("sales_receipt_amount"))));
        }

        // 客户 Top10、品类占比
        BigDecimal ship = cur.get("sales_ship_amount");
        List<Share> top = shares(queryService.queryInternal(BiQuery.of(List.of("sales_ship_amount"), List.of("customer"), r.from(), r.to(), null)
                .withSort("sales_ship_amount", "desc", 10)), "customer", "sales_ship_amount", ship, 10);
        List<Share> categories = shares(queryService.queryInternal(BiQuery.of(List.of("sales_ship_amount"), List.of("category"), r.from(), r.to(), null)
                .withSort("sales_ship_amount", "desc", 200)), "category", "sales_ship_amount", ship, 7);

        // 交付
        Delivery delivery = delivery(cur.get("on_time_delivery_rate"), today);

        // 质量：近 6 个月
        LocalDate qFrom = endMonth.minusMonths(5).atDay(1);
        Map<String, Map<String, Object>> qByMonth = index(queryService.queryInternal(BiQuery.of(List.of("iqc_lot_pass_rate", "fpy", "complaint_count"),
                List.of("date"), qFrom, endMonth.atEndOfMonth(), "month")));
        List<QualityPoint> quality = new ArrayList<>();
        for (YearMonth ym = endMonth.minusMonths(5); !ym.isAfter(endMonth); ym = ym.plusMonths(1)) {
            Map<String, Object> row = qByMonth.getOrDefault(ym.toString(), Map.of());
            quality.add(new QualityPoint(ym.toString(), (BigDecimal) row.get("iqc_lot_pass_rate"), (BigDecimal) row.get("fpy"),
                    dec(row.get("complaint_count"))));
        }

        // 库存结构
        List<InventoryPart> inventory = new ArrayList<>();
        for (Map<String, Object> row : queryService.queryInternal(BiQuery.of(List.of("inventory_amount"), List.of("warehouse_type"), r.from(), r.to(),
                null).withSort("inventory_amount", "desc", 50)).rows()) {
            inventory.add(new InventoryPart((String) row.get("warehouse_type"), (String) row.get("warehouse_type_label"), dec(row.get("inventory_amount"))));
        }
        return new Dashboard(r.from(), r.to(), r.compareFrom(), r.compareTo(), cmp.name(), finance, kpis, trend, top, delivery, categories, quality,
                inventory, cur.get("slow_moving_amount"), trendResult.dataUpdatedAt());
    }

    /** 无维度的指标合计 */
    public Map<String, BigDecimal> totals(List<String> metrics, LocalDate from, LocalDate to) {
        BiQueryResult res = queryService.queryInternal(BiQuery.of(metrics, List.of(), from, to, null));
        Map<String, BigDecimal> m = new HashMap<>();
        if (!res.rows().isEmpty()) {
            res.rows().get(0).forEach((k, v) -> {
                if (v instanceof BigDecimal d) m.put(k, d);
            });
        }
        return m;
    }

    static Kpi kpi(String code, Map<String, String> names, Map<String, BigDecimal> cur, Map<String, BigDecimal> prev, String extraCode,
                   BigDecimal extra, String route) {
        var def = MetricRegistry.get(code);
        BigDecimal v = cur.get(code);
        BigDecimal p = prev.get(code);
        boolean percent = def.unit() == com.erp.module.bi.service.metric.MetricDefinition.Unit.PERCENT;
        BigDecimal pct = percent ? null : changePct(v, p);
        BigDecimal pt = percent && v != null && p != null ? v.subtract(p).setScale(2, RoundingMode.HALF_UP) : null;
        var extraDef = extraCode == null ? null : MetricRegistry.get(extraCode);
        return new Kpi(code, names.getOrDefault(code, def.name()), def.unit().name(), v, p, pct, pt,
                extraDef == null ? null : names.getOrDefault(extraCode, extraDef.name()), extraDef == null ? null : extraDef.unit().name(), extra, route, null, null);
    }

    /** 变化率（%）：对比期为 0 或为空时无意义 */
    public static BigDecimal changePct(BigDecimal cur, BigDecimal prev) {
        if (cur == null || prev == null || prev.signum() == 0) return null;
        return cur.subtract(prev).multiply(BigDecimal.valueOf(100)).divide(prev.abs(), 1, RoundingMode.HALF_UP);
    }

    private Delivery delivery(BigDecimal onTimeRate, LocalDate today) {
        List<SalesOrderLineDTO> lines = salesOrderQueryApi.getOpenLines(OpenLineFilter.all());
        Set<Long> orderIds = new HashSet<>();
        lines.forEach(l -> orderIds.add(l.orderId()));
        Map<Long, SalesOrderHeaderDTO> headers = orderIds.isEmpty() ? Map.of() : salesOrderQueryApi.getOrderHeaders(orderIds);
        Map<Long, Optional<Long>> companies = new HashMap<>();
        BigDecimal open = BigDecimal.ZERO;
        int overdue = 0;
        for (SalesOrderLineDTO l : lines) {
            if (l.openQty() == null || l.openQty().signum() <= 0) continue;
            Long org = l.deptId() == null ? null : companies.computeIfAbsent(l.deptId(), d -> orgApi.getCompanyOf(d).map(OrgDTO::id)).orElse(null);
            if (!DataScopes.visible(org, l.deptId(), l.ownerId())) continue;
            SalesOrderHeaderDTO h = headers.get(l.orderId());
            BigDecimal rate = h == null || h.exchangeRate() == null ? BigDecimal.ONE : h.exchangeRate();
            BigDecimal price = l.basePriceInclTax() == null ? BigDecimal.ZERO : l.basePriceInclTax();
            open = open.add(l.openQty().multiply(price).multiply(rate));
            LocalDate due = l.dueDate();
            if (due != null && due.isBefore(today)) overdue++;
        }
        return new Delivery(onTimeRate, open.setScale(2, RoundingMode.HALF_UP), overdue);
    }

    private static Map<String, Map<String, Object>> index(BiQueryResult r) {
        Map<String, Map<String, Object>> m = new HashMap<>();
        r.rows().forEach(row -> m.put((String) row.get("date"), row));
        return m;
    }

    /** 前 n 项 + “其他” */
    private static List<Share> shares(BiQueryResult r, String dim, String metric, BigDecimal total, int n) {
        List<Share> list = new ArrayList<>();
        BigDecimal rest = BigDecimal.ZERO;
        int i = 0;
        for (Map<String, Object> row : r.rows()) {
            BigDecimal v = dec(row.get(metric));
            if (v.signum() <= 0) continue;
            if (i++ < n) list.add(new Share((String) row.get(dim), (String) row.get(dim + "_label"), v, share(v, total)));
            else rest = rest.add(v);
        }
        if (rest.signum() > 0) list.add(new Share(null, "其他", rest, share(rest, total)));
        return list;
    }

    private static BigDecimal share(BigDecimal v, BigDecimal total) {
        if (total == null || total.signum() <= 0) return null;
        return v.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    static BigDecimal dec(Object v) {
        return v instanceof BigDecimal d ? d : BigDecimal.ZERO;
    }
}
