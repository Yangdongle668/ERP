package com.erp.module.bi.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.bi.dal.dataobject.AiAnomalyDO;
import com.erp.module.bi.dal.dataobject.AiWeeklyReportDO;
import com.erp.module.bi.dal.mapper.AiWeeklyReportMapper;
import com.erp.module.bi.service.ai.LlmAdapter.LlmRequest;
import com.erp.module.bi.service.ai.LlmAdapter.LlmResult;
import com.erp.module.bi.service.ai.LlmAdapter.ToolHandler;
import com.erp.module.bi.service.dashboard.BiDashboardService;
import com.erp.module.bi.service.metric.BiMetricService;
import com.erp.module.bi.service.metric.MetricDefinition;
import com.erp.module.bi.service.metric.MetricRegistry;
import com.erp.module.bi.service.query.BiQuery;
import com.erp.module.bi.service.query.BiQueryResult;
import com.erp.module.bi.service.query.BiQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营周报（需求 13-04 第 2.3 节）：每周一 07:30 生成上周（周一～周日）全公司口径的周报——KPI 与环比、出货变化最大的客户、
 * 出货前 5 产品、交付与质量要点、异常摘要；大模型生成文字总结（未启用 AI 时使用模板）。
 * <p>全公司口径，只对数据范围为“全部”的 {@code bi:dashboard:view} 用户展示。
 */
@Slf4j
@Service
public class AiWeeklyReportService {

    static final List<String> KPI = List.of("sales_order_amount", "sales_ship_amount", "sales_receipt_amount", "purchase_amount",
            "on_time_delivery_rate", "iqc_lot_pass_rate", "fpy", "complaint_count", "inventory_amount", "gross_margin");

    private final BiQueryService queryService;
    private final BiMetricService metricService;
    private final AiWeeklyReportMapper reportMapper;
    private final AiAnomalyService anomalyService;
    private final AiChatService chatService;
    private final AiSettings settings;
    private final ObjectMapper json;

    public AiWeeklyReportService(BiQueryService queryService, BiMetricService metricService, AiWeeklyReportMapper reportMapper,
                                 AiAnomalyService anomalyService, AiChatService chatService, AiSettings settings, ObjectMapper json) {
        this.queryService = queryService;
        this.metricService = metricService;
        this.reportMapper = reportMapper;
        this.anomalyService = anomalyService;
        this.chatService = chatService;
        this.settings = settings;
        this.json = json;
    }

    /** 生成 today 所在周的上一周周报（重复生成时覆盖） */
    public AiWeeklyReportDO generate(LocalDate today) {
        LocalDate from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        LocalDate to = from.plusDays(6);
        Map<String, String> names = metricService.names();
        Map<String, Object> data = DataScopes.ignore(() -> collect(from, to, names));
        String summary = template(data);
        AiSettings.Snapshot s = settings.get();
        if (s.ready()) {
            try {
                LlmResult r = chatService.adapter().converse(new LlmRequest(s.baseUrl(), s.model(), s.apiKey(),
                        "你是 ERP 经营数据分析助手。根据给定的上周经营数据（JSON）写一份中文经营周报总结：300 字以内，分“总体”“销售与交付”“质量与库存”“关注事项”四段，"
                                + "只引用数据中出现的数字，变化用环比百分比表述，不要编造数据。",
                        List.of(), toJson(masked(data, s.mask())), List.of(), 2048, 0, 60), ToolHandler.NONE);
                if (r.text() != null && !r.text().isBlank()) summary = r.text().trim();
            } catch (RuntimeException e) {
                log.warn("AI 周报总结失败，使用模板", e);
            }
        }
        reportMapper.delete(new LambdaQueryWrapper<AiWeeklyReportDO>().eq(AiWeeklyReportDO::getWeekStart, from).isNull(AiWeeklyReportDO::getUserId));
        AiWeeklyReportDO row = new AiWeeklyReportDO();
        row.setWeekStart(from);
        row.setTitle("经营周报 " + from + " ~ " + to);
        row.setDataJson(toJson(data));
        row.setSummary(summary);
        reportMapper.insert(row);
        return reportMapper.selectById(row.getId());
    }

    /** 当前用户可查看的周报（全公司口径，需要数据范围为全部） */
    public List<AiWeeklyReportDO> list() {
        if (!SecurityUtils.currentDataScope().all()) return List.of();
        return reportMapper.selectList(new LambdaQueryWrapper<AiWeeklyReportDO>().isNull(AiWeeklyReportDO::getUserId)
                .orderByDesc(AiWeeklyReportDO::getWeekStart).last("LIMIT 52"));
    }

    Map<String, Object> collect(LocalDate from, LocalDate to, Map<String, String> names) {
        LocalDate pf = from.minusWeeks(1);
        LocalDate pt = to.minusWeeks(1);
        Map<String, BigDecimal> cur = totals(from, to);
        Map<String, BigDecimal> prev = totals(pf, pt);
        List<Map<String, Object>> kpis = new ArrayList<>();
        for (String code : KPI) {
            MetricDefinition m = MetricRegistry.get(code);
            Map<String, Object> k = new LinkedHashMap<>();
            k.put("code", code);
            k.put("name", names.getOrDefault(code, m.name()));
            k.put("unit", m.unit().name());
            k.put("value", cur.get(code));
            k.put("previous", prev.get(code));
            if (m.unit() == MetricDefinition.Unit.PERCENT) {
                k.put("changePt", cur.get(code) == null || prev.get(code) == null ? null : cur.get(code).subtract(prev.get(code)).setScale(2, RoundingMode.HALF_UP));
            } else {
                k.put("changePct", BiDashboardService.changePct(cur.get(code), prev.get(code)));
            }
            kpis.add(k);
        }
        // 出货变化最大的客户
        Map<String, BigDecimal> curCust = byDim("sales_ship_amount", "customer", from, to);
        Map<String, BigDecimal> prevCust = byDim("sales_ship_amount", "customer", pf, pt);
        List<Map<String, Object>> changes = new ArrayList<>();
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(curCust.keySet());
        keys.addAll(prevCust.keySet());
        for (String k : keys) {
            BigDecimal c = curCust.getOrDefault(k, BigDecimal.ZERO);
            BigDecimal p = prevCust.getOrDefault(k, BigDecimal.ZERO);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("customer", k);
            m.put("current", c);
            m.put("previous", p);
            m.put("delta", c.subtract(p));
            changes.add(m);
        }
        changes.sort(Comparator.comparing((Map<String, Object> m) -> ((BigDecimal) m.get("delta")).abs()).reversed());
        List<Map<String, Object>> topProducts = new ArrayList<>();
        byDim("sales_ship_amount", "material", from, to).entrySet().stream().limit(5).forEach(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("material", e.getKey());
            m.put("amount", e.getValue());
            topProducts.add(m);
        });
        List<Map<String, Object>> anomalies = new ArrayList<>();
        for (AiAnomalyDO a : anomalyService.between(from.plusDays(1), to.plusDays(1))) {
            if (anomalies.size() >= 10) break;
            anomalies.add(AiAnomalyService.brief(a, names));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", from.toString());
        data.put("to", to.toString());
        data.put("kpis", kpis);
        data.put("customerChanges", changes.size() > 10 ? changes.subList(0, 10) : changes);
        data.put("topProducts", topProducts);
        data.put("anomalies", anomalies);
        return data;
    }

    private Map<String, BigDecimal> totals(LocalDate from, LocalDate to) {
        BiQueryResult r = queryService.queryInternal(BiQuery.of(KPI, List.of(), from, to, null));
        Map<String, BigDecimal> m = new HashMap<>();
        if (!r.rows().isEmpty()) r.rows().get(0).forEach((k, v) -> {
            if (v instanceof BigDecimal d) m.put(k, d);
        });
        return m;
    }

    /** 维度名称 → 值（按值降序） */
    private Map<String, BigDecimal> byDim(String metric, String dim, LocalDate from, LocalDate to) {
        BiQueryResult r = queryService.queryInternal(BiQuery.of(List.of(metric), List.of(dim), from, to, null).withSort(metric, "desc", 200));
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        r.rows().forEach(row -> m.put((String) row.get(dim + "_label"), (BigDecimal) row.get(metric)));
        return m;
    }

    /** 脱敏：敏感指标只保留变化率（周报 KPI 中目前没有敏感指标，保留以防口径调整） */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> masked(Map<String, Object> data, boolean mask) {
        if (!mask) return data;
        Map<String, Object> copy = new LinkedHashMap<>(data);
        List<Map<String, Object>> kpis = new ArrayList<>();
        for (Map<String, Object> k : (List<Map<String, Object>>) data.get("kpis")) {
            MetricDefinition m = MetricRegistry.get((String) k.get("code"));
            if (m != null && m.sensitive()) {
                Map<String, Object> x = new LinkedHashMap<>(k);
                x.remove("value");
                x.remove("previous");
                kpis.add(x);
            } else {
                kpis.add(k);
            }
        }
        copy.put("kpis", kpis);
        return copy;
    }

    @SuppressWarnings("unchecked")
    static String template(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("总体：");
        for (Map<String, Object> k : (List<Map<String, Object>>) data.get("kpis")) {
            Object v = k.get("value");
            if (v == null) continue;
            sb.append(k.get("name")).append(" ").append(((BigDecimal) v).toPlainString());
            if ("PERCENT".equals(k.get("unit"))) sb.append("%");
            Object pct = k.get("changePct");
            Object pt = k.get("changePt");
            if (pct != null) sb.append("（环比 ").append(((BigDecimal) pct).signum() >= 0 ? "+" : "").append(((BigDecimal) pct).toPlainString()).append("%）");
            else if (pt != null) sb.append("（环比 ").append(((BigDecimal) pt).signum() >= 0 ? "+" : "").append(((BigDecimal) pt).toPlainString()).append(" 个百分点）");
            sb.append("；");
        }
        List<Map<String, Object>> anomalies = (List<Map<String, Object>>) data.get("anomalies");
        if (!anomalies.isEmpty()) sb.append("\n关注事项：上周检测到 ").append(anomalies.size()).append(" 项经营数据异常，详见异常页签。");
        return sb.toString();
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
