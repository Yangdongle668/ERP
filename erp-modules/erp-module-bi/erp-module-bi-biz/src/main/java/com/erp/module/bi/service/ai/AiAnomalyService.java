package com.erp.module.bi.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.bi.config.BiModuleConfig;
import com.erp.module.bi.dal.dataobject.AiAnomalyDO;
import com.erp.module.bi.dal.mapper.AiAnomalyMapper;
import com.erp.module.bi.service.ai.LlmAdapter.LlmRequest;
import com.erp.module.bi.service.ai.LlmAdapter.LlmResult;
import com.erp.module.bi.service.ai.LlmAdapter.ToolHandler;
import com.erp.module.bi.service.metric.BiMetricService;
import com.erp.module.bi.service.metric.MetricDefinition;
import com.erp.module.bi.service.metric.MetricRegistry;
import com.erp.module.bi.service.query.BiQuery;
import com.erp.module.bi.service.query.BiQueryResult;
import com.erp.module.bi.service.query.BiQueryService;
import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.purchase.api.supplier.SupplierApi;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.notify.NotifyApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 异常解读（需求 13-04 第 2.2 节）：每天 08:00 检测前一日——
 * <ul>
 *   <li>SIGMA：公司合计的日指标与过去 8 周同星期均值偏离超过 3σ；</li>
 *   <li>MOM：按维度（前 20 个客户 / 供应商）近 30 天与前 30 天相比变化超过 30%。</li>
 * </ul>
 * 统计方法先筛选异常，再交给大模型生成 2～3 句解释（未启用 AI 时使用模板说明）；结果以预警 AI_ANOMALY 推送。
 */
@Slf4j
@Service
public class AiAnomalyService {

    public static final String ALERT_TYPE = "AI_ANOMALY";
    static final BigDecimal MOM_THRESHOLD = BigDecimal.valueOf(30);
    static final BigDecimal WARNING_THRESHOLD = BigDecimal.valueOf(50);
    static final int TOP_N = 20;
    static final int MAX_ANOMALIES = 30;
    static final List<String> SIGMA_METRICS = List.of("sales_order_amount", "sales_ship_amount", "sales_receipt_amount", "purchase_amount");
    /** 按维度检测：指标 × 维度 */
    static final List<String[]> MOM_CHECKS = List.of(new String[]{"sales_ship_amount", "customer"}, new String[]{"sales_order_amount", "customer"},
            new String[]{"purchase_amount", "supplier"});

    private final BiQueryService queryService;
    private final BiMetricService metricService;
    private final AiAnomalyMapper anomalyMapper;
    private final AiSettings settings;
    private final AiChatService chatService;
    private final NotifyApi notifyApi;
    private final CustomerApi customerApi;
    private final SupplierApi supplierApi;

    public AiAnomalyService(BiQueryService queryService, BiMetricService metricService, AiAnomalyMapper anomalyMapper, AiSettings settings,
                            AiChatService chatService, NotifyApi notifyApi, CustomerApi customerApi, SupplierApi supplierApi) {
        this.queryService = queryService;
        this.metricService = metricService;
        this.anomalyMapper = anomalyMapper;
        this.settings = settings;
        this.chatService = chatService;
        this.notifyApi = notifyApi;
        this.customerApi = customerApi;
        this.supplierApi = supplierApi;
    }

    /** 检测 today 前一日的异常（重复运行时覆盖当日结果）；返回异常条数 */
    public int detect(LocalDate today) {
        LocalDate day = today.minusDays(1);
        Map<String, String> names = metricService.names();
        List<AiAnomalyDO> found = new ArrayList<>();
        DataScopes.ignore(() -> {
            for (String metric : SIGMA_METRICS) sigma(metric, day, names).ifPresent(found::add);
            for (String[] check : MOM_CHECKS) found.addAll(mom(check[0], check[1], day, names));
        });
        found.sort((a, b) -> b.getChangePct().abs().compareTo(a.getChangePct().abs()));
        List<AiAnomalyDO> list = found.size() > MAX_ANOMALIES ? new ArrayList<>(found.subList(0, MAX_ANOMALIES)) : found;
        explain(list, names);
        anomalyMapper.delete(new LambdaQueryWrapper<AiAnomalyDO>().eq(AiAnomalyDO::getDetectDate, today));
        for (AiAnomalyDO a : list) {
            a.setDetectDate(today);
            anomalyMapper.insert(a);
        }
        if (!list.isEmpty()) {
            boolean warning = list.stream().anyMatch(a -> "WARNING".equals(a.getLevel()));
            AiAnomalyDO top = list.get(0);
            String content = "共 " + list.size() + " 项，变化最大：" + names.getOrDefault(top.getMetricCode(), top.getMetricCode())
                    + (top.getDimLabel() == null ? "" : "（" + top.getDimLabel() + "）") + " " + top.getChangePct().toPlainString() + "%";
            notifyApi.alert(new AlertRaisedEvent(ALERT_TYPE + ":" + today, ALERT_TYPE, warning ? AlertRaisedEvent.Level.WARNING : AlertRaisedEvent.Level.INFO,
                    null, BiModuleConfig.PERM_DASHBOARD, "AI_ANOMALY", null, "经营数据异常解读（" + day + "）", content, "/bi/ai?tab=anomaly"));
        }
        return list.size();
    }

    /** 当前用户可见的异常：需要指标权限；按客户 / 供应商的异常还需在数据范围内 */
    public List<AiAnomalyDO> list(LocalDate from, LocalDate to) {
        LoginUser user = SecurityUtils.getLoginUser();
        LocalDate f = from == null ? LocalDate.now().minusDays(30) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        List<AiAnomalyDO> rows = anomalyMapper.selectList(new LambdaQueryWrapper<AiAnomalyDO>().between(AiAnomalyDO::getDetectDate, f, t)
                .orderByDesc(AiAnomalyDO::getDetectDate).orderByAsc(AiAnomalyDO::getId));
        List<AiAnomalyDO> visible = new ArrayList<>();
        for (AiAnomalyDO a : rows) {
            MetricDefinition m = MetricRegistry.get(a.getMetricCode());
            if (m == null || !user.hasPermission(m.permission())) continue;
            if (!dimVisible(a)) continue;
            visible.add(a);
        }
        return visible;
    }

    private boolean dimVisible(AiAnomalyDO a) {
        if (a.getDimension() == null || a.getDimValue() == null || SecurityUtils.currentDataScope().all()) return true;
        try {
            Long id = Long.valueOf(a.getDimValue());
            if ("customer".equals(a.getDimension())) {
                CustomerDTO c = customerApi.getCustomer(id).orElse(null);
                return c != null && DataScopes.visible(null, c.deptId(), c.ownerId());
            }
            if ("supplier".equals(a.getDimension())) {
                return supplierApi.getSupplier(id).map(s -> DataScopes.visible(null, null, s.buyerId())).orElse(false);
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
        return true;
    }

    // ==================== 统计检测 ====================

    java.util.Optional<AiAnomalyDO> sigma(String metric, LocalDate day, Map<String, String> names) {
        LocalDate from = day.minusWeeks(8);
        BiQueryResult r = queryService.queryInternal(BiQuery.of(List.of(metric), List.of("date"), from, day, "day"));
        Map<String, BigDecimal> byDate = new HashMap<>();
        r.rows().forEach(row -> byDate.put((String) row.get("date"), (BigDecimal) row.get(metric)));
        BigDecimal cur = byDate.getOrDefault(day.toString(), BigDecimal.ZERO);
        List<BigDecimal> hist = new ArrayList<>();
        for (int k = 1; k <= 8; k++) hist.add(byDate.getOrDefault(day.minusWeeks(k).toString(), BigDecimal.ZERO));
        long nonZero = hist.stream().filter(v -> v.signum() != 0).count();
        if (nonZero < 4) return java.util.Optional.empty();
        double mean = hist.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double sd = Math.sqrt(hist.stream().mapToDouble(v -> Math.pow(v.doubleValue() - mean, 2)).sum() / hist.size());
        if (sd <= 0 || Math.abs(cur.doubleValue() - mean) <= 3 * sd || mean == 0) return java.util.Optional.empty();
        BigDecimal base = BigDecimal.valueOf(mean).setScale(4, RoundingMode.HALF_UP);
        BigDecimal pct = cur.subtract(base).multiply(BigDecimal.valueOf(100)).divide(base.abs(), 2, RoundingMode.HALF_UP);
        return java.util.Optional.of(anomaly(metric, null, null, null, cur, base, pct, "SIGMA"));
    }

    List<AiAnomalyDO> mom(String metric, String dim, LocalDate day, Map<String, String> names) {
        LocalDate curFrom = day.minusDays(29);
        LocalDate prevTo = curFrom.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(29);
        BiQueryResult prev = queryService.queryInternal(BiQuery.of(List.of(metric), List.of(dim), prevFrom, prevTo, null).withSort(metric, "desc", TOP_N));
        BiQueryResult cur = queryService.queryInternal(BiQuery.of(List.of(metric), List.of(dim), curFrom, day, null).withSort(metric, "desc", 1000));
        Map<String, Map<String, Object>> curBy = new HashMap<>();
        cur.rows().forEach(row -> curBy.put((String) row.get(dim), row));
        List<AiAnomalyDO> list = new ArrayList<>();
        for (Map<String, Object> p : prev.rows()) {
            String key = (String) p.get(dim);
            if (key == null) continue;
            BigDecimal base = (BigDecimal) p.get(metric);
            if (base == null || base.signum() <= 0) continue;
            Map<String, Object> c = curBy.get(key);
            BigDecimal v = c == null || c.get(metric) == null ? BigDecimal.ZERO : (BigDecimal) c.get(metric);
            BigDecimal pct = v.subtract(base).multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
            if (pct.abs().compareTo(MOM_THRESHOLD) < 0) continue;
            list.add(anomaly(metric, dim, key, (String) p.get(dim + "_label"), v, base, pct, "MOM"));
        }
        return list;
    }

    private static AiAnomalyDO anomaly(String metric, String dim, String value, String label, BigDecimal cur, BigDecimal base, BigDecimal pct,
                                       String method) {
        AiAnomalyDO a = new AiAnomalyDO();
        a.setMetricCode(metric);
        a.setDimension(dim);
        a.setDimValue(value);
        a.setDimLabel(label);
        a.setCurrentValue(cur);
        a.setBaseValue(base);
        a.setChangePct(pct);
        a.setMethod(method);
        a.setLevel(pct.abs().compareTo(WARNING_THRESHOLD) >= 0 ? "WARNING" : "INFO");
        return a;
    }

    // ==================== 解读 ====================

    static final Pattern LINE = Pattern.compile("(?m)^\\s*#(\\d+)\\s+(.+)$");

    /** 大模型生成 2～3 句解释；未启用或失败时使用模板说明 */
    void explain(List<AiAnomalyDO> list, Map<String, String> names) {
        list.forEach(a -> a.setExplanation(template(a, names)));
        AiSettings.Snapshot s = settings.get();
        if (list.isEmpty() || !s.ready()) return;
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            AiAnomalyDO a = list.get(i);
            MetricDefinition m = MetricRegistry.get(a.getMetricCode());
            boolean hide = s.mask() && m != null && m.sensitive();
            data.append('#').append(i + 1).append(' ').append(names.getOrDefault(a.getMetricCode(), a.getMetricCode()));
            if (a.getDimLabel() != null) data.append("｜").append(MetricRegistry.DIMENSIONS.getOrDefault(a.getDimension(), a.getDimension()))
                    .append(" ").append(a.getDimLabel());
            data.append("｜方法 ").append("SIGMA".equals(a.getMethod()) ? "与过去 8 周同星期均值相比（3σ）" : "近 30 天与前 30 天相比");
            if (!hide) data.append("｜本期 ").append(a.getCurrentValue().toPlainString()).append("｜基准 ").append(a.getBaseValue().toPlainString());
            data.append("｜变化 ").append(a.getChangePct().toPlainString()).append("%\n");
        }
        String system = "你是 ERP 经营数据分析助手。下面是统计方法筛出的经营数据异常，请为每一项写 2～3 句中文解释："
                + "说明变化幅度和可能的业务原因（只能作为推测，用“可能”表述），并给出建议关注点。不要编造数据中没有的数字。"
                + "每项输出一行，格式为“#序号 解释”，不要输出其他内容。";
        try {
            LlmResult r = chatService.adapter(s.provider()).converse(new LlmRequest(s.model(), s.apiKey(), system, List.of(), data.toString(), List.of(),
                    2048, 0, 60), ToolHandler.NONE);
            Matcher m = LINE.matcher(r.text() == null ? "" : r.text());
            while (m.find()) {
                int idx = Integer.parseInt(m.group(1)) - 1;
                if (idx >= 0 && idx < list.size()) list.get(idx).setExplanation(AiChatService.truncate(m.group(2).trim(), 2000));
            }
        } catch (RuntimeException e) {
            log.warn("AI 异常解读失败，使用模板说明", e);
        }
    }

    static String template(AiAnomalyDO a, Map<String, String> names) {
        String name = names.getOrDefault(a.getMetricCode(), a.getMetricCode());
        String subject = a.getDimLabel() == null ? "公司" : MetricRegistry.DIMENSIONS.getOrDefault(a.getDimension(), "") + "「" + a.getDimLabel() + "」";
        String dir = a.getChangePct().signum() >= 0 ? "上升" : "下降";
        String basis = "SIGMA".equals(a.getMethod()) ? "较过去 8 周同星期均值" : "近 30 天较前 30 天";
        return subject + name + basis + dir + " " + a.getChangePct().abs().toPlainString() + "%，建议核实相关订单、出货或付款计划是否有变化。";
    }

    /** 某期间的异常（周报用，不按用户过滤） */
    List<AiAnomalyDO> between(LocalDate from, LocalDate to) {
        return anomalyMapper.selectList(new LambdaQueryWrapper<AiAnomalyDO>().between(AiAnomalyDO::getDetectDate, from, to)
                .orderByDesc(AiAnomalyDO::getDetectDate));
    }

    static Map<String, Object> brief(AiAnomalyDO a, Map<String, String> names) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", a.getDetectDate());
        m.put("metric", names.getOrDefault(a.getMetricCode(), a.getMetricCode()));
        m.put("dimension", a.getDimLabel());
        m.put("changePct", a.getChangePct());
        m.put("level", a.getLevel());
        return m;
    }
}
