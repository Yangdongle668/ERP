package com.erp.module.bi.service.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.bi.api.BiErrorCodes;
import com.erp.module.bi.config.BiModuleConfig;
import com.erp.module.bi.dal.mapper.BiAggFinanceMapper;
import com.erp.module.bi.dal.mapper.BiAggInventoryMonthlyMapper;
import com.erp.module.bi.dal.mapper.BiAggInventorySnapshotMapper;
import com.erp.module.bi.dal.mapper.BiAggProductionMapper;
import com.erp.module.bi.dal.mapper.BiAggPurchaseMapper;
import com.erp.module.bi.dal.mapper.BiAggQualityMapper;
import com.erp.module.bi.dal.mapper.BiAggSalesMapper;
import com.erp.module.bi.service.etl.BiEtlService;
import com.erp.module.bi.service.metric.BiMetricService;
import com.erp.module.bi.service.metric.MetricDefinition;
import com.erp.module.bi.service.metric.MetricDefinition.Mode;
import com.erp.module.bi.service.metric.MetricDefinition.Source;
import com.erp.module.bi.service.metric.MetricDefinition.Unit;
import com.erp.module.bi.service.metric.MetricRegistry;
import com.erp.module.bi.service.query.BiQueryResult.Column;
import com.erp.module.bi.service.query.BiQueryResult.MetricMeta;
import com.erp.module.engineering.api.category.MaterialCategoryApi;
import com.erp.module.system.api.param.ParamApi;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * BI 通用查询服务（需求 13-01 第 6 节）：按指标定义在汇总表上聚合，校验指标权限与维度白名单；数据范围由汇总表 Mapper 的
 * {@code @DataScope}（公司 / 部门 / 负责人）过滤。SQL 只由指标库常量和维度白名单列组成，筛选值全部参数化。
 */
@Service
public class BiQueryService {

    public static final List<String> GRANULARITIES = List.of("day", "month", "quarter", "year");
    static final int MAX_METRICS = 12;
    static final int MAX_DIMENSIONS = 3;
    static final int DEFAULT_LIMIT = 500;
    static final int MAX_LIMIT = 5000;
    static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMM");

    private final Map<Source, BaseMapperX<?>> mappers = new EnumMap<>(Source.class);
    private final BiMetricService metricService;
    private final BiLabelResolver labelResolver;
    private final MaterialCategoryApi categoryApi;
    private final ParamApi paramApi;
    private final BiEtlService etlService;

    public BiQueryService(BiAggSalesMapper salesMapper, BiAggPurchaseMapper purchaseMapper, BiAggProductionMapper productionMapper,
                          BiAggQualityMapper qualityMapper, BiAggInventorySnapshotMapper snapshotMapper, BiAggInventoryMonthlyMapper monthlyMapper,
                          BiAggFinanceMapper financeMapper, BiMetricService metricService, BiLabelResolver labelResolver, MaterialCategoryApi categoryApi,
                          ParamApi paramApi, BiEtlService etlService) {
        mappers.put(Source.SALES, salesMapper);
        mappers.put(Source.SPECIAL, salesMapper);
        mappers.put(Source.PURCHASE, purchaseMapper);
        mappers.put(Source.PRODUCTION, productionMapper);
        mappers.put(Source.QUALITY, qualityMapper);
        mappers.put(Source.INV_SNAPSHOT, snapshotMapper);
        mappers.put(Source.INV_MONTHLY, monthlyMapper);
        mappers.put(Source.FINANCE, financeMapper);
        this.metricService = metricService;
        this.labelResolver = labelResolver;
        this.categoryApi = categoryApi;
        this.paramApi = paramApi;
        this.etlService = etlService;
    }

    /** 页面 / AI 调用：以当前用户校验指标权限 */
    public BiQueryResult query(BiQuery q) {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        return execute(q, user == null ? null : user::hasPermission);
    }

    /** 内部调用（驾驶舱由页面自行控制卡片权限、后台任务无登录用户）：不校验指标权限，数据范围照常生效 */
    public BiQueryResult queryInternal(BiQuery q) {
        return execute(q, p -> true);
    }

    /** 单个指标在区间内的合计（无维度） */
    public BigDecimal total(String metric, LocalDate from, LocalDate to) {
        BiQueryResult r = queryInternal(BiQuery.of(List.of(metric), List.of(), from, to, "month"));
        return r.rows().isEmpty() ? null : (BigDecimal) r.rows().get(0).get(metric);
    }

    // ==================== 执行 ====================

    interface PermissionCheck {
        boolean has(String permission);
    }

    /** 结果行累加器：维度取值 + 各基础指标的分子 / 分母 */
    private static final class Acc {
        final String bucket;
        final Map<String, String> dims;
        final Map<String, BigDecimal[]> values = new HashMap<>();

        Acc(String bucket, Map<String, String> dims) {
            this.bucket = bucket;
            this.dims = dims;
        }
    }

    private BiQueryResult execute(BiQuery q, PermissionCheck perm) {
        if (q == null || q.metrics() == null || q.metrics().isEmpty()) throw BizException.of(BiErrorCodes.QUERY_INVALID, "至少选择一个指标");
        List<String> metricCodes = new ArrayList<>(new LinkedHashSet<>(q.metrics()));
        if (metricCodes.size() > MAX_METRICS) throw BizException.of(BiErrorCodes.QUERY_INVALID, "指标最多 " + MAX_METRICS + " 个");
        List<String> dims = q.dimensions() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(q.dimensions()));
        if (dims.size() > MAX_DIMENSIONS) throw BizException.of(BiErrorCodes.QUERY_INVALID, "维度最多 " + MAX_DIMENSIONS + " 个");
        for (String d : dims) {
            if (!MetricRegistry.DIMENSIONS.containsKey(d)) throw BizException.of(BiErrorCodes.QUERY_INVALID, "未知维度 " + d);
        }
        LocalDate today = LocalDate.now();
        LocalDate from = q.from() == null ? today.withDayOfMonth(1) : q.from();
        LocalDate to = q.to() == null ? today : q.to();
        if (from.isAfter(to)) throw BizException.of(BiErrorCodes.QUERY_INVALID, "开始日期晚于结束日期");
        if (ChronoUnit.DAYS.between(from, to) > 366L * 5) throw BizException.of(BiErrorCodes.QUERY_INVALID, "日期区间不能超过 5 年");
        String gran = q.granularity() == null || q.granularity().isBlank() ? "month" : q.granularity().toLowerCase(Locale.ROOT);
        if (!GRANULARITIES.contains(gran)) throw BizException.of(BiErrorCodes.QUERY_INVALID, "未知粒度 " + q.granularity());
        Map<String, List<String>> rawFilters = q.filters() == null ? Map.of() : q.filters();
        Map<String, String> names = metricService.names();

        List<MetricDefinition> requested = new ArrayList<>();
        for (String code : metricCodes) {
            MetricDefinition m = MetricRegistry.find(code).orElseThrow(() -> BizException.of(BiErrorCodes.METRIC_NOT_EXISTS, code));
            String name = names.getOrDefault(code, m.name());
            if (!perm.has(m.permission())) throw BizException.of(BiErrorCodes.METRIC_FORBIDDEN, name);
            for (String d : dims) {
                if (!m.dimColumns().containsKey(d)) throw BizException.of(BiErrorCodes.DIMENSION_NOT_ALLOWED, name, MetricRegistry.DIMENSIONS.get(d));
            }
            for (Map.Entry<String, List<String>> f : rawFilters.entrySet()) {
                if (f.getValue() == null || f.getValue().isEmpty()) continue;
                if (!MetricRegistry.DIMENSIONS.containsKey(f.getKey()) || "date".equals(f.getKey())) {
                    throw BizException.of(BiErrorCodes.QUERY_INVALID, "不支持的筛选条件 " + f.getKey());
                }
                if (!m.dimColumns().containsKey(f.getKey())) {
                    throw BizException.of(BiErrorCodes.DIMENSION_NOT_ALLOWED, name, MetricRegistry.DIMENSIONS.get(f.getKey()));
                }
            }
            requested.add(m);
        }
        // 展开派生指标的依赖
        LinkedHashSet<MetricDefinition> base = new LinkedHashSet<>();
        for (MetricDefinition m : requested) {
            if (m.isDerived()) m.deps().forEach(d -> base.add(MetricRegistry.get(d)));
            else base.add(m);
        }
        boolean hasDate = dims.contains("date");
        if (hasDate && "day".equals(gran) && base.stream().anyMatch(m -> m.source().monthly)) gran = "month";
        Map<String, List<Object>> filters = resolveFilters(rawFilters);

        Map<String, Acc> rows = new LinkedHashMap<>();
        // 按（来源, 时间聚合方式）分组，每组一条 SQL
        Map<String, List<MetricDefinition>> groups = new LinkedHashMap<>();
        for (MetricDefinition m : base) {
            if (m.isSpecial()) continue;
            groups.computeIfAbsent(m.source().name() + "|" + m.mode().name(), k -> new ArrayList<>()).add(m);
        }
        int slowDays = paramApi.getInt(BiModuleConfig.P_SLOW_DAYS);
        for (List<MetricDefinition> group : groups.values()) {
            runGroup(group, dims, filters, from, to, gran, slowDays, rows);
        }
        for (MetricDefinition m : base) {
            if (m.isSpecial()) runSpecial(m, hasDate, from, to, gran, rows);
        }

        // 维度名称
        Map<String, Map<String, String>> labels = new HashMap<>();
        for (String d : dims) {
            if ("date".equals(d)) continue;
            Set<String> values = new HashSet<>();
            rows.values().forEach(a -> values.add(a.dims.get(d)));
            labels.put(d, labelResolver.resolve(d, values));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Acc a : rows.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String d : dims) {
                String v = "date".equals(d) ? a.bucket : a.dims.get(d);
                row.put(d, v);
                row.put(d + "_label", "date".equals(d) ? v : labelResolver.label(labels.get(d), v));
            }
            int days = bucketDays(hasDate ? a.bucket : null, gran, from, to);
            Map<String, BigDecimal> baseValues = new HashMap<>();
            for (MetricDefinition m : base) baseValues.put(m.code(), value(m, a.values.get(m.code())));
            for (MetricDefinition m : requested) {
                row.put(m.code(), m.isDerived() ? m.derive().apply(baseValues, days) : baseValues.get(m.code()));
            }
            out.add(row);
        }
        sort(out, q, dims, requested, hasDate);
        int limit = q.limit() == null || q.limit() <= 0 ? DEFAULT_LIMIT : Math.min(q.limit(), MAX_LIMIT);
        boolean truncated = out.size() > limit;
        if (truncated) out = new ArrayList<>(out.subList(0, limit));

        List<Column> columns = new ArrayList<>();
        dims.forEach(d -> columns.add(new Column(d, MetricRegistry.DIMENSIONS.get(d), "DIMENSION", null)));
        List<MetricMeta> metas = new ArrayList<>();
        for (MetricDefinition m : requested) {
            String name = names.getOrDefault(m.code(), m.name());
            columns.add(new Column(m.code(), name, "METRIC", m.unit().name()));
            metas.add(new MetricMeta(m.code(), name, m.unit().name(), m.description(), m.source().label, m.sensitive()));
        }
        return new BiQueryResult(columns, out, metas, from, to, hasDate ? gran : null, truncated, etlService.lastSuccessAt());
    }

    private Map<String, List<Object>> resolveFilters(Map<String, List<String>> raw) {
        Map<String, List<Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : raw.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            String dim = e.getKey();
            List<Object> values = new ArrayList<>();
            for (String v : e.getValue()) {
                if (v == null || v.isBlank()) continue;
                if (BiLabelResolver.isIdDim(dim)) {
                    long id;
                    try {
                        id = Long.parseLong(v.trim());
                    } catch (NumberFormatException ex) {
                        throw BizException.of(BiErrorCodes.QUERY_INVALID, MetricRegistry.DIMENSIONS.get(dim) + " 取值 " + v);
                    }
                    if ("category".equals(dim)) {
                        List<Long> ids = categoryApi.getDescendantIds(id);
                        values.addAll(ids.isEmpty() ? List.of(id) : ids);
                    } else {
                        values.add(id);
                    }
                } else {
                    values.add(v.trim());
                }
            }
            if (!values.isEmpty()) result.put(dim, values);
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void runGroup(List<MetricDefinition> group, List<String> dims, Map<String, List<Object>> filters, LocalDate from, LocalDate to,
                          String gran, int slowDays, Map<String, Acc> rows) {
        MetricDefinition first = group.get(0);
        Source source = first.source();
        boolean hasDate = dims.contains("date");
        boolean needDate = first.mode() != Mode.SUM;
        String dateCol = source.dateColumn;
        List<String> select = new ArrayList<>();
        List<String> groupBy = new ArrayList<>();
        String bucketExpr = hasDate ? bucketExpr(source, gran) : null;
        if (bucketExpr != null) {
            select.add(bucketExpr + " AS b_date");
            groupBy.add(bucketExpr);
        }
        if (needDate) {
            select.add(dateCol + " AS s_date");
            groupBy.add(dateCol);
        }
        for (String d : dims) {
            if ("date".equals(d)) continue;
            String col = first.dimColumns().get(d);
            select.add(col + " AS d_" + d);
            groupBy.add(col);
        }
        for (int i = 0; i < group.size(); i++) {
            MetricDefinition m = group.get(i);
            select.add(m.numerator().replace(MetricRegistry.SLOW_DAYS, String.valueOf(slowDays)) + " AS n" + i);
            if (m.denominator() != null) {
                select.add(m.denominator().replace(MetricRegistry.SLOW_DAYS, String.valueOf(slowDays)) + " AS q" + i);
            }
        }
        QueryWrapper w = new QueryWrapper<>();
        w.select(select.toArray(String[]::new));
        if (source.monthly) w.between(dateCol, from.format(PERIOD), to.format(PERIOD));
        else w.between(dateCol, from, to);
        for (Map.Entry<String, List<Object>> f : filters.entrySet()) {
            w.in(first.dimColumns().get(f.getKey()), f.getValue());
        }
        if (!groupBy.isEmpty()) w.groupBy(groupBy.get(0), groupBy.subList(1, groupBy.size()).toArray());
        List<Map<String, Object>> result = ((BaseMapperX) mappers.get(source)).selectMaps(w);
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> r : result) {
            if (r == null) continue;
            Map<String, Object> lower = new HashMap<>();
            r.forEach((k, v) -> lower.put(k.toLowerCase(Locale.ROOT), v));
            data.add(lower);
        }
        // LAST：每个时间桶只取最后一个快照日 / 期间；AVG：按快照日数平均
        Map<String, String> lastDate = new HashMap<>();
        Map<String, Set<String>> dates = new HashMap<>();
        if (needDate) {
            for (Map<String, Object> r : data) {
                String b = hasDate ? bucket(r.get("b_date"), gran) : "";
                String s = str(r.get("s_date"));
                lastDate.merge(b, s, (x, y) -> x.compareTo(y) >= 0 ? x : y);
                dates.computeIfAbsent(b, k -> new HashSet<>()).add(s);
            }
        }
        for (Map<String, Object> r : data) {
            String b = hasDate ? bucket(r.get("b_date"), gran) : "";
            if (first.mode() == Mode.LAST && !Objects.equals(lastDate.get(b), str(r.get("s_date")))) continue;
            Map<String, String> dv = new LinkedHashMap<>();
            for (String d : dims) {
                if (!"date".equals(d)) dv.put(d, str(r.get("d_" + d)));
            }
            Acc acc = rows.computeIfAbsent(b + "|" + dv, k -> new Acc(b, dv));
            BigDecimal divisor = first.mode() == Mode.AVG ? BigDecimal.valueOf(Math.max(1, dates.getOrDefault(b, Set.of()).size())) : BigDecimal.ONE;
            for (int i = 0; i < group.size(); i++) {
                MetricDefinition m = group.get(i);
                BigDecimal n = num(r.get("n" + i));
                BigDecimal den = m.denominator() == null ? null : num(r.get("q" + i));
                if (divisor.compareTo(BigDecimal.ONE) != 0) {
                    n = n.divide(divisor, 6, RoundingMode.HALF_UP);
                    if (den != null) den = den.divide(divisor, 6, RoundingMode.HALF_UP);
                }
                BigDecimal[] v = acc.values.computeIfAbsent(m.code(), k -> new BigDecimal[]{BigDecimal.ZERO, m.denominator() == null ? null : BigDecimal.ZERO});
                v[0] = v[0].add(n);
                if (den != null) v[1] = v[1].add(den);
            }
        }
    }

    /** 新客户数：客户首次接单日期（order_amount > 0 的最早日期）落在区间内的客户数 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void runSpecial(MetricDefinition m, boolean hasDate, LocalDate from, LocalDate to, String gran, Map<String, Acc> rows) {
        QueryWrapper<Object> w = new QueryWrapper<>();
        w.select("customer_id AS c", "MIN(stat_date) AS f").gt("order_amount", 0).isNotNull("customer_id").groupBy("customer_id");
        List<Map<String, Object>> result = ((BaseMapperX) mappers.get(Source.SPECIAL)).selectMaps(w);
        for (Map<String, Object> r : result) {
            Object f = null;
            for (Map.Entry<String, Object> e : r.entrySet()) {
                if ("f".equalsIgnoreCase(e.getKey())) f = e.getValue();
            }
            if (f == null) continue;
            LocalDate first = LocalDate.parse(str(f).substring(0, 10));
            if (first.isBefore(from) || first.isAfter(to)) continue;
            String b = hasDate ? bucket(bucketOfDate(first, gran), gran) : "";
            Acc acc = rows.computeIfAbsent(b + "|{}", k -> new Acc(b, new LinkedHashMap<>()));
            BigDecimal[] v = acc.values.computeIfAbsent(m.code(), k -> new BigDecimal[]{BigDecimal.ZERO, null});
            v[0] = v[0].add(BigDecimal.ONE);
        }
    }

    // ==================== 工具 ====================

    static final String QUARTER_EXPR = "CONCAT(SUBSTRING(period, 1, 4), CASE WHEN SUBSTRING(period, 5, 2) <= '03' THEN 'Q1' "
            + "WHEN SUBSTRING(period, 5, 2) <= '06' THEN 'Q2' WHEN SUBSTRING(period, 5, 2) <= '09' THEN 'Q3' ELSE 'Q4' END)";

    static String bucketExpr(Source source, String gran) {
        return switch (gran) {
            case "day" -> source.monthly ? "period" : source.dateColumn;
            case "quarter" -> QUARTER_EXPR;
            case "year" -> "SUBSTRING(period, 1, 4)";
            default -> "period";
        };
    }

    /** SQL 分桶值 → 统一格式：日 yyyy-MM-dd、月 yyyy-MM、季 yyyyQn、年 yyyy */
    static String bucket(Object raw, String gran) {
        String s = str(raw);
        if (s == null) return "";
        return switch (gran) {
            case "day" -> s.length() >= 10 ? s.substring(0, 10) : s;
            case "month" -> s.length() == 6 ? s.substring(0, 4) + "-" + s.substring(4) : s;
            default -> s;
        };
    }

    static String bucketOfDate(LocalDate d, String gran) {
        return switch (gran) {
            case "day" -> d.toString();
            case "quarter" -> d.getYear() + "Q" + ((d.getMonthValue() - 1) / 3 + 1);
            case "year" -> String.valueOf(d.getYear());
            default -> d.format(PERIOD);
        };
    }

    /** 时间桶与查询区间重叠的天数（派生指标的日均计算） */
    static int bucketDays(String bucket, String gran, LocalDate from, LocalDate to) {
        LocalDate s = from;
        LocalDate e = to;
        if (bucket != null && !bucket.isEmpty()) {
            try {
                switch (gran) {
                    case "day" -> {
                        return 1;
                    }
                    case "month" -> {
                        YearMonth ym = YearMonth.parse(bucket);
                        s = ym.atDay(1);
                        e = ym.atEndOfMonth();
                    }
                    case "quarter" -> {
                        int y = Integer.parseInt(bucket.substring(0, 4));
                        int qn = Integer.parseInt(bucket.substring(5));
                        s = LocalDate.of(y, (qn - 1) * 3 + 1, 1);
                        e = s.plusMonths(3).minusDays(1);
                    }
                    case "year" -> {
                        int y = Integer.parseInt(bucket);
                        s = LocalDate.of(y, 1, 1);
                        e = LocalDate.of(y, 12, 31);
                    }
                    default -> {
                    }
                }
            } catch (RuntimeException ex) {
                return (int) ChronoUnit.DAYS.between(from, to) + 1;
            }
            if (s.isBefore(from)) s = from;
            if (e.isAfter(to)) e = to;
        }
        return Math.max(1, (int) ChronoUnit.DAYS.between(s, e) + 1);
    }

    static BigDecimal value(MetricDefinition m, BigDecimal[] v) {
        if (v == null) return m.denominator() == null ? zero(m.unit()) : null;
        if (m.denominator() == null) return scale(v[0], m.unit());
        if (v[1] == null || v[1].signum() == 0) return null;
        int sc = m.unit() == Unit.PRICE ? 4 : 2;
        return v[0].multiply(BigDecimal.valueOf(m.factor())).divide(v[1], sc, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero(Unit unit) {
        return scale(BigDecimal.ZERO, unit);
    }

    private static BigDecimal scale(BigDecimal v, Unit unit) {
        return switch (unit) {
            case COUNT -> v.setScale(0, RoundingMode.HALF_UP);
            case QTY, PRICE -> v.setScale(4, RoundingMode.HALF_UP);
            default -> v.setScale(2, RoundingMode.HALF_UP);
        };
    }

    private static void sort(List<Map<String, Object>> rows, BiQuery q, List<String> dims, List<MetricDefinition> metrics, boolean hasDate) {
        String key = q.sort();
        boolean desc;
        if (key == null || key.isBlank() || (!dims.contains(key) && metrics.stream().noneMatch(m -> m.code().equals(key)))) {
            if (hasDate) {
                rows.sort(Comparator.comparing(r -> (String) r.get("date"), Comparator.nullsLast(Comparator.naturalOrder())));
                return;
            }
            String k = metrics.get(0).code();
            rows.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get(k), Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
            return;
        }
        desc = "desc".equalsIgnoreCase(q.order());
        Comparator<Map<String, Object>> c;
        if (dims.contains(key)) {
            String col = "date".equals(key) ? key : key + "_label";
            c = Comparator.comparing(r -> (String) r.get(col), Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            c = Comparator.comparing(r -> (BigDecimal) r.get(key), Comparator.nullsFirst(Comparator.naturalOrder()));
        }
        rows.sort(desc ? c.reversed() : c);
    }

    static BigDecimal num(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal d) return d;
        return new BigDecimal(v.toString());
    }

    static String str(Object v) {
        return v == null ? null : v.toString();
    }
}
