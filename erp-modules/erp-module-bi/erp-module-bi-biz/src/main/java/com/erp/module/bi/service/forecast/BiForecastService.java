package com.erp.module.bi.service.forecast;

import com.erp.common.exception.BizException;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.bi.api.BiErrorCodes;
import com.erp.module.bi.service.query.BiQuery;
import com.erp.module.bi.service.query.BiQueryResult;
import com.erp.module.bi.service.query.BiQueryService;
import com.erp.module.sales.api.forecast.ForecastApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 销售预测建议（需求 13-04 2.4，P2）：按物料取最近 24 个月的出货数量，历史满 12 个月的物料用 {@link ForecastModel}（线性趋势 × 季节指数）
 * 预测本月起的未来 3～6 个月；业务 / 计划员确认后一键生成销售预测草稿（{@link ForecastApi#createDraft}）。
 *
 * <p>建议基于全公司数据，只对数据范围为“全部”的用户提供；生成草稿时在服务端重新计算，不接收前端提交的数量。
 */
@Service
public class BiForecastService {

    static final String METRIC = "sales_ship_qty";
    static final int TOP_MATERIALS = 200;
    static final int BATCH = 40;
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMM");

    public record HistoryPoint(String month, BigDecimal qty) {
    }

    /**
     * @param historyMonths 有出货记录以来的月数（最多 24）；mape 为最近 3 个月回测的平均绝对百分比误差（%），历史不足 15 个月时为空
     */
    public record Suggestion(Long materialId, String materialLabel, int historyMonths, List<HistoryPoint> history, Map<String, BigDecimal> forecast,
                             String method, Double mape) {
    }

    public record Result(String startPeriod, String endPeriod, int months, List<Suggestion> suggestions) {
    }

    public record Generated(Long forecastId, int materialCount) {
    }

    private final BiQueryService queryService;
    private final ForecastApi forecastApi;

    public BiForecastService(BiQueryService queryService, ForecastApi forecastApi) {
        this.queryService = queryService;
        this.forecastApi = forecastApi;
    }

    /** 未来 months（3～6）个月的建议，按预测总量降序 */
    public Result suggestions(int months) {
        if (!SecurityUtils.currentDataScope().all()) throw new BizException(BiErrorCodes.FORECAST_SCOPE);
        int horizon = Math.max(3, Math.min(6, months));
        YearMonth lastFull = YearMonth.now().minusMonths(1);
        YearMonth first = lastFull.minusMonths(23);
        YearMonth start = YearMonth.now();
        List<Long> top = topMaterials(first, lastFull);
        List<Suggestion> out = new ArrayList<>();
        for (int i = 0; i < top.size(); i += BATCH) {
            List<Long> batch = top.subList(i, Math.min(i + BATCH, top.size()));
            Map<Long, Map<YearMonth, BigDecimal>> data = new LinkedHashMap<>();
            Map<Long, String> labels = new HashMap<>();
            BiQueryResult r = queryService.queryInternal(new BiQuery(List.of(METRIC), List.of("material", "date"),
                    Map.of("material", batch.stream().map(String::valueOf).toList()), first.atDay(1), lastFull.atEndOfMonth(), "month", null, null, 5000));
            for (Map<String, Object> row : r.rows()) {
                Long mid = Long.valueOf(String.valueOf(row.get("material")));
                labels.put(mid, String.valueOf(row.get("material_label")));
                Object q = row.get(METRIC);
                data.computeIfAbsent(mid, k -> new TreeMap<>()).put(YearMonth.parse(String.valueOf(row.get("date"))), q == null ? BigDecimal.ZERO : (BigDecimal) q);
            }
            for (Long mid : batch) {
                Suggestion s = build(mid, labels.get(mid), data.get(mid), first, lastFull, start, horizon);
                if (s != null) out.add(s);
            }
        }
        out.sort((a, b) -> total(b).compareTo(total(a)));
        return new Result(start.format(PERIOD), start.plusMonths(horizon - 1).format(PERIOD), horizon, out);
    }

    /** 一键生成销售预测草稿（服务端重算，只包含所选物料中有建议的） */
    @Transactional(rollbackFor = Exception.class)
    public Generated generate(List<Long> materialIds, int months) {
        if (materialIds == null || materialIds.isEmpty()) throw new BizException(BiErrorCodes.FORECAST_NO_SELECTION);
        LoginUser user = SecurityUtils.getLoginUser();
        if (!user.hasPermission("sales:forecast:create")) throw new BizException(BiErrorCodes.FORECAST_NO_CREATE_PERMISSION);
        Result r = suggestions(months);
        Map<Long, Map<String, BigDecimal>> quantities = new LinkedHashMap<>();
        for (Suggestion s : r.suggestions()) {
            if (materialIds.contains(s.materialId())) quantities.put(s.materialId(), s.forecast());
        }
        if (quantities.isEmpty()) throw new BizException(BiErrorCodes.FORECAST_NOT_AVAILABLE);
        Long id = forecastApi.createDraft(new ForecastApi.DraftRequest("销售预测建议 " + r.startPeriod() + "～" + r.endPeriod(), r.startPeriod(), r.endPeriod(),
                "由 BI 销售预测建议生成（线性趋势 × 季节指数，基于最近 24 个月出货数量），请业务 / 计划员核对后发布", quantities));
        return new Generated(id, quantities.size());
    }

    // ==================== 内部 ====================

    private List<Long> topMaterials(YearMonth from, YearMonth to) {
        BiQueryResult r = queryService.queryInternal(new BiQuery(List.of(METRIC), List.of("material"), null, from.atDay(1), to.atEndOfMonth(), "month",
                METRIC, "desc", TOP_MATERIALS));
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : r.rows()) {
            Object q = row.get(METRIC);
            if (row.get("material") != null && q instanceof BigDecimal b && b.signum() > 0) ids.add(Long.valueOf(String.valueOf(row.get("material"))));
        }
        return ids;
    }

    /** 从首次出货月起构造历史（缺月为 0），不足 12 个月返回 null */
    private Suggestion build(Long materialId, String label, Map<YearMonth, BigDecimal> byMonth, YearMonth windowFirst, YearMonth lastFull, YearMonth start,
                             int horizon) {
        if (byMonth == null || byMonth.isEmpty()) return null;
        YearMonth firstShip = byMonth.entrySet().stream().filter(e -> e.getValue().signum() > 0).map(Map.Entry::getKey).findFirst().orElse(null);
        if (firstShip == null) return null;
        YearMonth from = firstShip.isBefore(windowFirst) ? windowFirst : firstShip;
        int n = (int) (lastFull.getYear() * 12L + lastFull.getMonthValue() - (from.getYear() * 12L + from.getMonthValue())) + 1;
        if (n < ForecastModel.MIN_HISTORY) return null;
        double[] h = new double[n];
        List<HistoryPoint> history = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            YearMonth ym = from.plusMonths(i);
            BigDecimal q = byMonth.getOrDefault(ym, BigDecimal.ZERO);
            h[i] = q.doubleValue();
            if (i >= n - 12) history.add(new HistoryPoint(ym.toString(), q));
        }
        ForecastModel.Result m = ForecastModel.forecast(h, lastFull.getMonthValue(), horizon);
        // 模型从“最近完整月的下一个月”即本月开始预测，本月对应第 0 个
        Map<String, BigDecimal> forecast = new LinkedHashMap<>();
        for (int i = 0; i < horizon; i++) {
            BigDecimal q = BigDecimal.valueOf(m.forecast()[i]).setScale(0, RoundingMode.HALF_UP);
            forecast.put(start.plusMonths(i).format(PERIOD), q);
        }
        return new Suggestion(materialId, label, n, history, forecast, m.seasonalUsed() ? "TREND_SEASONAL" : "TREND", m.mape());
    }

    private static BigDecimal total(Suggestion s) {
        return s.forecast().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
