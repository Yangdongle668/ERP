package com.erp.module.bi.service.ai;

import com.erp.common.exception.BizException;
import com.erp.module.bi.api.BiErrorCodes;
import com.erp.module.bi.service.ai.LlmAdapter.ToolSpec;
import com.erp.module.bi.service.metric.BiMetricService.MetricInfo;
import com.erp.module.bi.service.metric.MetricDefinition;
import com.erp.module.bi.service.metric.MetricRegistry;
import com.erp.module.bi.service.query.BiQuery;
import com.erp.module.bi.service.query.BiQueryResult;
import com.erp.module.bi.service.query.BiQueryResult.Column;
import com.erp.module.bi.service.query.BiQueryService;
import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.category.MaterialCategoryApi;
import com.erp.module.engineering.api.category.MaterialCategoryDTO;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.api.supplier.SupplierApi;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 问数的唯一取数工具 bi_query（需求 13-04 第 2.1 节）：模型只能选择指标、维度、筛选、期间，不能生成 SQL；
 * 以当前用户身份调用 {@link BiQueryService#query}（指标权限与数据范围生效）。
 * <p>脱敏（AI-R03）：敏感指标的数值不交给模型，改为排名、占比和变化率；返回给用户的数据表仍为真实数值。
 */
@Component
public class BiQueryTool {

    public static final String NAME = "bi_query";
    /** 交给模型的最大行数 */
    static final int MODEL_ROWS = 50;

    /** 一次工具调用的结果：modelContent 交给模型；result 为真实数据（页面展示）；error 为失败原因 */
    public record Execution(Map<String, Object> input, String modelContent, BiQueryResult result, String error) {
    }

    private final BiQueryService queryService;
    private final CustomerApi customerApi;
    private final SupplierApi supplierApi;
    private final MaterialApi materialApi;
    private final MaterialCategoryApi categoryApi;

    public BiQueryTool(BiQueryService queryService, CustomerApi customerApi, SupplierApi supplierApi, MaterialApi materialApi,
                       MaterialCategoryApi categoryApi) {
        this.queryService = queryService;
        this.customerApi = customerApi;
        this.supplierApi = supplierApi;
        this.materialApi = materialApi;
        this.categoryApi = categoryApi;
    }

    /** 工具定义：指标只列出当前用户有权限的（AI-R02） */
    public ToolSpec spec(List<MetricInfo> visible) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("metrics", Map.of("type", "array", "minItems", 1, "maxItems", 6, "description", "指标编码（只能从系统提示的指标清单中选择）",
                "items", Map.of("type", "string", "enum", visible.stream().map(m -> m.def().code()).toList())));
        props.put("dimensions", Map.of("type", "array", "maxItems", 3, "description", "分组维度编码，可为空（只返回合计）",
                "items", Map.of("type", "string", "enum", List.copyOf(MetricRegistry.DIMENSIONS.keySet()))));
        props.put("filters", Map.of("type", "object", "description",
                "筛选条件：维度编码 → 取值数组。客户、供应商、物料、物料类别可以直接写名称或编码（系统按名称匹配），国家写国家代码，仓库类型写 RAW/FG 等编码",
                "additionalProperties", Map.of("type", "array", "items", Map.of("type", "string"))));
        props.put("from", Map.of("type", "string", "description", "开始日期 yyyy-MM-dd（含）"));
        props.put("to", Map.of("type", "string", "description", "结束日期 yyyy-MM-dd（含）"));
        props.put("granularity", Map.of("type", "string", "enum", BiQueryService.GRANULARITIES, "description", "按日期分组时的粒度，默认 month"));
        props.put("sort", Map.of("type", "string", "description", "排序列：指标编码或维度编码"));
        props.put("order", Map.of("type", "string", "enum", List.of("asc", "desc")));
        props.put("limit", Map.of("type", "integer", "minimum", 1, "maximum", 200, "description", "返回行数，默认 50"));
        return new ToolSpec(NAME, "按指标库查询经营数据（汇总表，已按当前用户的数据权限过滤）。返回 JSON 表格：列为维度和指标，比率类指标已是百分数。"
                + "需要 Top N、排名时用 sort + order + limit；需要趋势时加 date 维度和 granularity。", props, List.of("metrics", "from", "to"));
    }

    /** 执行一次工具调用；mask 为是否脱敏 */
    public Execution execute(Map<String, Object> input, boolean mask) {
        try {
            BiQuery q = parse(input);
            BiQueryResult r = queryService.query(q);
            return new Execution(input, modelContent(q, r, mask), r, null);
        } catch (BizException e) {
            return new Execution(input, e.getMessage(), null, e.getMessage());
        } catch (IllegalArgumentException | DateTimeParseException | ClassCastException e) {
            String msg = "查询参数不正确：" + e.getMessage();
            return new Execution(input, msg, null, msg);
        }
    }

    BiQuery parse(Map<String, Object> in) {
        List<String> metrics = strings(in.get("metrics"));
        List<String> dims = strings(in.get("dimensions"));
        LocalDate from = in.get("from") == null ? null : LocalDate.parse(in.get("from").toString().trim());
        LocalDate to = in.get("to") == null ? null : LocalDate.parse(in.get("to").toString().trim());
        Map<String, List<String>> filters = new LinkedHashMap<>();
        if (in.get("filters") instanceof Map<?, ?> f) {
            for (Map.Entry<?, ?> e : f.entrySet()) {
                String dim = String.valueOf(e.getKey());
                List<String> values = new ArrayList<>();
                for (String v : strings(e.getValue())) values.addAll(resolveValue(dim, v));
                if (values.isEmpty()) throw BizException.of(BiErrorCodes.QUERY_INVALID, "找不到" + MetricRegistry.DIMENSIONS.getOrDefault(dim, dim) + " " + e.getValue());
                filters.put(dim, values);
            }
        }
        Integer limit = in.get("limit") instanceof Number n ? n.intValue() : 50;
        return new BiQuery(metrics, dims, filters, from, to, in.get("granularity") == null ? null : in.get("granularity").toString(),
                in.get("sort") == null ? null : in.get("sort").toString(), in.get("order") == null ? null : in.get("order").toString(), Math.min(limit, 200));
    }

    /** 名称 / 编码 → ID（模型不知道内部 ID） */
    List<String> resolveValue(String dim, String value) {
        if (value == null || value.isBlank()) return List.of();
        String v = value.trim();
        if (v.chars().allMatch(Character::isDigit) && v.length() > 6) return List.of(v);
        return switch (dim) {
            case "customer" -> customerApi.search(v, null, 5).stream().map(CustomerDTO::id).map(String::valueOf).toList();
            case "supplier" -> supplierApi.search(v, null, 5).stream().map(SupplierDTO::id).map(String::valueOf).toList();
            case "material" -> materialApi.search(v, null, 5).stream().map(MaterialDTO::id).map(String::valueOf).toList();
            case "category" -> categoryApi.listAll().stream().filter(c -> c.name().equalsIgnoreCase(v) || c.code().equalsIgnoreCase(v)
                    || c.name().contains(v)).map(MaterialCategoryDTO::id).map(String::valueOf).toList();
            case "warehouse_type", "inspect_type", "country" -> List.of(v.toUpperCase(Locale.ROOT));
            default -> List.of(v);
        };
    }

    /** 交给模型的 JSON：列用中文名称；敏感指标以排名 / 占比 / 变化率代替数值 */
    String modelContent(BiQuery q, BiQueryResult r, boolean mask) {
        List<Column> dims = r.columns().stream().filter(c -> "DIMENSION".equals(c.kind())).toList();
        List<Column> metrics = r.columns().stream().filter(c -> "METRIC".equals(c.kind())).toList();
        Map<String, Map<Integer, BigDecimal[]>> masked = new HashMap<>();
        boolean anyMasked = false;
        for (Column m : metrics) {
            MetricDefinition def = MetricRegistry.get(m.key());
            if (mask && def != null && def.sensitive()) {
                masked.put(m.key(), maskColumn(r.rows(), m.key(), dims));
                anyMasked = true;
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < r.rows().size() && i < MODEL_ROWS; i++) {
            Map<String, Object> row = r.rows().get(i);
            Map<String, Object> out = new LinkedHashMap<>();
            for (Column d : dims) out.put(d.label(), row.get("date".equals(d.key()) ? "date" : d.key() + "_label"));
            for (Column m : metrics) {
                Map<Integer, BigDecimal[]> mk = masked.get(m.key());
                if (mk == null) {
                    out.put(m.label(), row.get(m.key()));
                } else {
                    BigDecimal[] v = mk.get(i);
                    out.put(m.label() + "_排名", v[0]);
                    out.put(m.label() + "_占比%", v[1]);
                    if (v[2] != null) out.put(m.label() + "_环比%", v[2]);
                }
            }
            rows.add(out);
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("期间", r.from() + " ~ " + r.to());
        if (r.granularity() != null) content.put("粒度", r.granularity());
        if (q.filters() != null && !q.filters().isEmpty()) content.put("筛选", q.filters());
        List<Map<String, Object>> metas = new ArrayList<>();
        r.metrics().forEach(m -> metas.add(Map.of("编码", m.code(), "名称", m.name(), "单位", m.unit(), "口径", m.description())));
        content.put("指标", metas);
        content.put("行数", r.rows().size());
        if (r.rows().size() > MODEL_ROWS || r.truncated()) content.put("说明", "只返回前 " + Math.min(MODEL_ROWS, r.rows().size()) + " 行");
        if (anyMasked) content.put("脱敏", "敏感数值（成本、价格、毛利额等）按系统设置不提供具体数值，只提供排名、占比和环比变化率；回答中不要推测具体金额");
        content.put("数据", rows);
        return toJson(content);
    }

    /** 每行：[排名, 占比%, 环比%]（环比仅在有日期维度时按同一其他维度的上一期计算） */
    static Map<Integer, BigDecimal[]> maskColumn(List<Map<String, Object>> rows, String key, List<Column> dims) {
        Map<Integer, BigDecimal[]> result = new HashMap<>();
        List<Integer> idx = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < rows.size(); i++) {
            idx.add(i);
            BigDecimal v = (BigDecimal) rows.get(i).get(key);
            if (v != null && v.signum() > 0) total = total.add(v);
        }
        idx.sort(Comparator.comparing((Integer i) -> (BigDecimal) rows.get(i).get(key), Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        Map<Integer, Integer> rank = new HashMap<>();
        for (int r = 0; r < idx.size(); r++) rank.put(idx.get(r), r + 1);
        boolean hasDate = dims.stream().anyMatch(d -> "date".equals(d.key()));
        Map<String, BigDecimal> prevByGroup = new HashMap<>();
        List<Integer> byDate = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) byDate.add(i);
        if (hasDate) byDate.sort(Comparator.comparing(i -> String.valueOf(rows.get(i).get("date"))));
        for (int i : byDate) {
            BigDecimal v = (BigDecimal) rows.get(i).get(key);
            BigDecimal share = v == null || total.signum() == 0 ? null : v.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
            BigDecimal change = null;
            if (hasDate) {
                StringBuilder g = new StringBuilder();
                for (Column d : dims) if (!"date".equals(d.key())) g.append(rows.get(i).get(d.key())).append('|');
                BigDecimal prev = prevByGroup.put(g.toString(), v);
                if (prev != null && v != null && prev.signum() != 0) {
                    change = v.subtract(prev).multiply(BigDecimal.valueOf(100)).divide(prev.abs(), 1, RoundingMode.HALF_UP);
                }
            }
            result.put(i, new BigDecimal[]{BigDecimal.valueOf(rank.get(i)), share, change});
        }
        return result;
    }

    private static List<String> strings(Object o) {
        List<String> list = new ArrayList<>();
        if (o instanceof Collection<?> c) {
            for (Object x : c) if (x != null) list.add(x.toString());
        } else if (o != null && !o.toString().isBlank()) {
            list.add(o.toString());
        }
        return list;
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper()
            .findAndRegisterModules();

    static String toJson(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
