package com.erp.module.bi.service.subscription;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.LoginUserLoader;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.bi.api.BiErrorCodes;
import com.erp.module.bi.dal.dataobject.BiSubscriptionDO;
import com.erp.module.bi.dal.mapper.BiSubscriptionMapper;
import com.erp.module.bi.service.metric.BiMetricService;
import com.erp.module.bi.service.metric.MetricDefinition;
import com.erp.module.bi.service.metric.MetricRegistry;
import com.erp.module.bi.service.query.BiQuery;
import com.erp.module.bi.service.query.BiQueryResult;
import com.erp.module.bi.service.query.BiQueryService;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 报表订阅（需求 13-03 第 8 节，P2）：订阅是“保存的查询 + 周期”——指标、分组维度、筛选、相对期间（昨日 / 上周 / 上月 / 本月至今）、
 * 频率（每天 / 每周几 / 每月几号）。定时任务 {@code BI_SUBSCRIPTION} 每天 07:40 以订阅人的身份（指标权限与数据范围都按订阅人）执行查询，
 * 生成文字报表，通过工作台消息发送，勾选“同时发邮件”时再发邮件。
 *
 * <p>与需求文档的差异：不生成 PDF 附件，报表以文字表格（合计 + 前 N 个分组）发送，详细数据在 BI 页面查看。
 */
@Service
public class BiSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(BiSubscriptionService.class);

    public static final List<String> PERIODS = List.of("YESTERDAY", "LAST_WEEK", "LAST_MONTH", "MONTH_TO_DATE");
    public static final List<String> FREQUENCIES = List.of("DAILY", "WEEKLY", "MONTHLY");
    static final int MAX_PER_USER = 20;

    /** 保存 / 修改请求 */
    public record Save(String name, List<String> metrics, String dimension, Map<String, List<String>> filters, String periodType, Integer topN,
                       String frequency, Integer weekday, Integer monthday, Boolean sendEmail, Boolean enabled) {
    }

    public record View(Long id, String name, List<String> metrics, List<String> metricNames, String dimension, Map<String, List<String>> filters,
                       String periodType, int topN, String frequency, Integer weekday, Integer monthday, boolean sendEmail, boolean enabled,
                       LocalDate lastSentOn, String lastStatus, String lastMessage) {
    }

    /** 生成的报表：标题 + 正文（文字） */
    public record Report(String title, String content) {
    }

    private final BiSubscriptionMapper mapper;
    private final BiQueryService queryService;
    private final BiMetricService metricService;
    private final LoginUserLoader userLoader;
    private final NotifyApi notifyApi;
    private final ObjectMapper json;

    public BiSubscriptionService(BiSubscriptionMapper mapper, BiQueryService queryService, BiMetricService metricService, LoginUserLoader userLoader,
                                 NotifyApi notifyApi, ObjectMapper json) {
        this.mapper = mapper;
        this.queryService = queryService;
        this.metricService = metricService;
        this.userLoader = userLoader;
        this.notifyApi = notifyApi;
        this.json = json;
    }

    // ==================== 维护（只能操作自己的订阅） ====================

    public List<View> list() {
        Long uid = SecurityUtils.getLoginUser().id();
        Map<String, String> names = metricService.names();
        return mapper.selectList(new LambdaQueryWrapper<BiSubscriptionDO>().eq(BiSubscriptionDO::getUserId, uid).orderByDesc(BiSubscriptionDO::getId))
                .stream().map(d -> view(d, names)).toList();
    }

    public Long create(Save req) {
        Long uid = SecurityUtils.getLoginUser().id();
        if (mapper.selectCount(new LambdaQueryWrapper<BiSubscriptionDO>().eq(BiSubscriptionDO::getUserId, uid)) >= MAX_PER_USER) {
            throw new BizException(BiErrorCodes.SUB_LIMIT);
        }
        BiSubscriptionDO d = new BiSubscriptionDO();
        d.setUserId(uid);
        d.setEnabled(true);
        fill(d, req);
        mapper.insert(d);
        return d.getId();
    }

    public void update(Long id, Save req) {
        BiSubscriptionDO d = own(id);
        fill(d, req);
        mapper.updateByIdOrFail(d);
    }

    public void delete(Long id) {
        mapper.deleteById(own(id).getId());
    }

    /** 立即按当前设置生成并发送一次（预览 / 测试） */
    public Report sendNow(Long id) {
        BiSubscriptionDO d = own(id);
        return deliver(d, LocalDate.now());
    }

    private BiSubscriptionDO own(Long id) {
        BiSubscriptionDO d = id == null ? null : mapper.selectById(id);
        if (d == null || !d.getUserId().equals(SecurityUtils.getLoginUser().id())) throw new BizException(BiErrorCodes.SUB_NOT_EXISTS);
        return d;
    }

    private void fill(BiSubscriptionDO d, Save r) {
        String name = r.name() == null ? "" : r.name().trim();
        if (name.isEmpty() || name.length() > 64) throw BizException.of(BiErrorCodes.SUB_INVALID, "请填写订阅名称（64 字以内）");
        List<String> metrics = r.metrics() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(r.metrics()));
        if (metrics.isEmpty() || metrics.size() > 8) throw BizException.of(BiErrorCodes.SUB_INVALID, "请选择 1～8 个指标");
        for (String m : metrics) {
            if (MetricRegistry.find(m).isEmpty()) throw BizException.of(BiErrorCodes.METRIC_NOT_EXISTS, m);
        }
        String dim = StringUtils.hasText(r.dimension()) ? r.dimension().trim() : null;
        if (dim != null && (!MetricRegistry.DIMENSIONS.containsKey(dim) || "date".equals(dim))) throw BizException.of(BiErrorCodes.SUB_INVALID, "分组维度 " + dim);
        if (!PERIODS.contains(r.periodType())) throw BizException.of(BiErrorCodes.SUB_INVALID, "请选择期间");
        if (!FREQUENCIES.contains(r.frequency())) throw BizException.of(BiErrorCodes.SUB_INVALID, "请选择发送频率");
        Integer weekday = r.weekday();
        Integer monthday = r.monthday();
        if ("WEEKLY".equals(r.frequency()) && (weekday == null || weekday < 1 || weekday > 7)) throw BizException.of(BiErrorCodes.SUB_INVALID, "请选择每周几发送");
        if ("MONTHLY".equals(r.frequency()) && (monthday == null || monthday < 1 || monthday > 28)) throw BizException.of(BiErrorCodes.SUB_INVALID, "每月发送日为 1～28");
        d.setSubName(name);
        d.setMetrics(String.join(",", metrics));
        d.setDimension(dim);
        d.setFilters(r.filters() == null || r.filters().isEmpty() ? null : write(r.filters()));
        d.setPeriodType(r.periodType());
        d.setTopN(r.topN() == null ? 10 : Math.max(1, Math.min(50, r.topN())));
        d.setFrequency(r.frequency());
        d.setWeekday("WEEKLY".equals(r.frequency()) ? weekday : null);
        d.setMonthday("MONTHLY".equals(r.frequency()) ? monthday : null);
        d.setSendEmail(Boolean.TRUE.equals(r.sendEmail()));
        if (r.enabled() != null) d.setEnabled(r.enabled());
    }

    private View view(BiSubscriptionDO d, Map<String, String> names) {
        List<String> metrics = Arrays.asList(d.getMetrics().split(","));
        return new View(d.getId(), d.getSubName(), metrics, metrics.stream().map(m -> names.getOrDefault(m, m)).toList(), d.getDimension(), filters(d),
                d.getPeriodType(), d.getTopN() == null ? 10 : d.getTopN(), d.getFrequency(), d.getWeekday(), d.getMonthday(), Boolean.TRUE.equals(d.getSendEmail()),
                Boolean.TRUE.equals(d.getEnabled()), d.getLastSentOn(), d.getLastStatus(), d.getLastMessage());
    }

    // ==================== 定时发送 ====================

    /** 今天是否该发送 */
    public static boolean due(BiSubscriptionDO d, LocalDate today) {
        if (!Boolean.TRUE.equals(d.getEnabled()) || today.equals(d.getLastSentOn())) return false;
        return switch (d.getFrequency()) {
            case "DAILY" -> true;
            case "WEEKLY" -> d.getWeekday() != null && today.getDayOfWeek().getValue() == d.getWeekday();
            case "MONTHLY" -> d.getMonthday() != null && today.getDayOfMonth() == d.getMonthday();
            default -> false;
        };
    }

    /** 发送今天到期的订阅，返回结果摘要；单个订阅失败只记录，不影响其他订阅 */
    public String runDue(LocalDate today) {
        int ok = 0;
        int failed = 0;
        for (BiSubscriptionDO d : mapper.selectList(new LambdaQueryWrapper<BiSubscriptionDO>().eq(BiSubscriptionDO::getEnabled, true))) {
            if (!due(d, today)) continue;
            try {
                deliver(d, today);
                ok++;
            } catch (RuntimeException e) {
                failed++;
            }
        }
        return "发送 " + ok + " 个，失败 " + failed + " 个";
    }

    /** 以订阅人身份生成报表并发送，记录结果；失败时记录原因并抛出 */
    Report deliver(BiSubscriptionDO d, LocalDate today) {
        try {
            Optional<LoginUser> user = userLoader.load(d.getUserId());
            if (user.isEmpty()) throw new IllegalStateException("订阅人已停用或不存在");
            Report r = SecurityUtils.runAs(user.get(), () -> render(d, today));
            notifyApi.message(new MessageSendEvent(List.of(d.getUserId()), MessageSendEvent.Type.REMIND, r.title(), r.content(), "/bi/subscription",
                    Boolean.TRUE.equals(d.getSendEmail())));
            record(d, today, "SUCCESS", null);
            return r;
        } catch (RuntimeException e) {
            String msg = e instanceof BizException ? e.getMessage() : e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("[BI 订阅] {} 发送失败：{}", d.getSubName(), msg);
            record(d, today, "FAILED", msg);
            throw e;
        }
    }

    private void record(BiSubscriptionDO d, LocalDate today, String status, String message) {
        BiSubscriptionDO u = new BiSubscriptionDO();
        u.setId(d.getId());
        u.setLastSentOn("SUCCESS".equals(status) ? today : d.getLastSentOn());
        u.setLastStatus(status);
        u.setLastMessage(message == null ? null : message.length() > 250 ? message.substring(0, 250) : message);
        mapper.updateById(u);
    }

    // ==================== 生成 ====================

    /** 期间：昨日 / 上周（周一～周日）/ 上月 / 本月至今 */
    public static LocalDate[] period(String type, LocalDate today) {
        return switch (type) {
            case "YESTERDAY" -> new LocalDate[]{today.minusDays(1), today.minusDays(1)};
            case "LAST_WEEK" -> {
                LocalDate mon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
                yield new LocalDate[]{mon, mon.plusDays(6)};
            }
            case "LAST_MONTH" -> {
                YearMonth ym = YearMonth.from(today).minusMonths(1);
                yield new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
            }
            default -> new LocalDate[]{today.withDayOfMonth(1), today};
        };
    }

    static String periodLabel(String type) {
        return switch (type) {
            case "YESTERDAY" -> "昨日";
            case "LAST_WEEK" -> "上周";
            case "LAST_MONTH" -> "上月";
            default -> "本月至今";
        };
    }

    /** 按当前登录用户（已切换为订阅人）执行查询并渲染文字报表：先合计，再按维度取前 N 行 */
    Report render(BiSubscriptionDO d, LocalDate today) {
        LocalDate[] p = period(d.getPeriodType(), today);
        List<String> metrics = Arrays.asList(d.getMetrics().split(","));
        Map<String, List<String>> filters = filters(d);
        Map<String, String> names = metricService.names();
        BiQuery total = new BiQuery(metrics, List.of(), filters, p[0], p[1], "month", null, null, 1);
        BiQueryResult tr = queryService.query(total);
        StringBuilder sb = new StringBuilder();
        sb.append("期间：").append(p[0]).append(p[0].equals(p[1]) ? "" : " ~ " + p[1]).append("（").append(periodLabel(d.getPeriodType())).append("）\n");
        Map<String, Object> totals = tr.rows().isEmpty() ? Map.of() : tr.rows().get(0);
        for (String m : metrics) sb.append(names.getOrDefault(m, m)).append("：").append(format(m, totals.get(m))).append('\n');
        if (d.getDimension() != null) {
            String dimName = MetricRegistry.DIMENSIONS.getOrDefault(d.getDimension(), d.getDimension());
            int n = d.getTopN() == null ? 10 : d.getTopN();
            BiQueryResult gr = queryService.query(new BiQuery(metrics, List.of(d.getDimension()), filters, p[0], p[1], "month", metrics.get(0), "desc", n));
            sb.append("\n按").append(dimName).append("（前 ").append(n).append("）\n");
            int i = 1;
            for (Map<String, Object> row : gr.rows()) {
                sb.append(i++).append(". ").append(row.get(d.getDimension() + "_label"));
                for (String m : metrics) sb.append("  ").append(names.getOrDefault(m, m)).append(' ').append(format(m, row.get(m)));
                sb.append('\n');
            }
            if (gr.rows().isEmpty()) sb.append("（没有数据）\n");
        }
        if (tr.dataUpdatedAt() != null) sb.append("\n数据更新于 ").append(tr.dataUpdatedAt().toString().replace('T', ' '), 0, 16);
        return new Report("[报表订阅] " + d.getSubName() + "（" + periodLabel(d.getPeriodType()) + "）", sb.toString());
    }

    static String format(String metric, Object value) {
        if (!(value instanceof BigDecimal v)) return "-";
        MetricDefinition.Unit unit = MetricRegistry.get(metric).unit();
        return switch (unit) {
            case PERCENT -> v.setScale(1, RoundingMode.HALF_UP) + "%";
            case QTY, COUNT -> new DecimalFormat("#,##0.##").format(v);
            case DAYS -> v.setScale(1, RoundingMode.HALF_UP) + " 天";
            default -> new DecimalFormat("#,##0.00").format(v);
        };
    }

    private Map<String, List<String>> filters(BiSubscriptionDO d) {
        if (!StringUtils.hasText(d.getFilters())) return null;
        try {
            return json.readValue(d.getFilters(), new TypeReference<Map<String, List<String>>>() {
            });
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
