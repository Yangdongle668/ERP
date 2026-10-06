package com.erp.module.fx.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.erp.common.exception.BizException;
import com.erp.common.util.Decimals;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.framework.maintenance.DataRestoredEvent;
import com.erp.module.fx.api.FxErrorCodes;
import com.erp.module.fx.api.FxPair;
import com.erp.module.fx.api.FxQuoteDTO;
import com.erp.module.fx.api.FxRateApi;
import com.erp.module.fx.api.FxRateUpdatedEvent;
import com.erp.module.fx.api.FxRateUpdatedEvent.Kind;
import com.erp.module.fx.config.FxModuleConfig;
import com.erp.module.fx.controller.vo.FxVOs.DailyRow;
import com.erp.module.fx.controller.vo.FxVOs.FxStatus;
import com.erp.module.fx.controller.vo.FxVOs.MonthlyRow;
import com.erp.module.fx.controller.vo.FxVOs.QuoteRow;
import com.erp.module.fx.dal.dataobject.FxDailyRateDO;
import com.erp.module.fx.dal.dataobject.FxMonthlyRateDO;
import com.erp.module.fx.dal.dataobject.FxQuoteDO;
import com.erp.module.fx.dal.mapper.FxDailyRateMapper;
import com.erp.module.fx.dal.mapper.FxMonthlyRateMapper;
import com.erp.module.fx.dal.mapper.FxQuoteMapper;
import com.erp.module.fx.service.FxQuoteSource.Quote;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.currency.RateType;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.param.ParamApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 实时汇率（需求 16-实时汇率）：取得中国银行现汇买入价（USD_CNY、EUR_CNY，EUR_USD 为交叉汇率），内存缓存 15 分钟；
 * 当天报价的平均值为日平均汇率（当天结束后结算），当月日平均汇率的平均值为月平均汇率（月份结束后结算）；
 * 本位币为人民币时把 USD、EUR 的日平均 / 月平均汇率写入系统汇率表（日汇率 / 月末汇率，来源“自动”），并发布 {@link FxRateUpdatedEvent}。
 * 报价、日平均、月平均数据保存 3 年。
 */
@Slf4j
@Service
public class FxService implements FxRateApi {

    /** R02：缓存有效期 = 轮询间隔 */
    public static final Duration CACHE_TTL = Duration.ofMinutes(15);
    static final int KEEP_YEARS = 3;
    private static final String CNY = "CNY";

    private final FxQuoteSource source;
    private final FxQuoteMapper quoteMapper;
    private final FxDailyRateMapper dailyMapper;
    private final FxMonthlyRateMapper monthlyMapper;
    private final CurrencyApi currencyApi;
    private final ParamApi paramApi;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate tx;
    private final Clock clock = Clock.systemDefaultZone();

    private final Map<FxPair, FxQuoteDTO> cache = new EnumMap<>(FxPair.class);
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile LocalDateTime lastSuccessAt;
    private volatile LocalDateTime lastAttemptAt;
    private volatile String lastError;
    private volatile int consecutiveFailures;
    private volatile LocalDateTime nextRunAt;
    private volatile boolean polling;

    public FxService(FxQuoteSource source, FxQuoteMapper quoteMapper, FxDailyRateMapper dailyMapper, FxMonthlyRateMapper monthlyMapper,
                     CurrencyApi currencyApi, ParamApi paramApi, DomainEventPublisher eventPublisher, PlatformTransactionManager transactionManager) {
        this.source = source;
        this.quoteMapper = quoteMapper;
        this.dailyMapper = dailyMapper;
        this.monthlyMapper = monthlyMapper;
        this.currencyApi = currencyApi;
        this.paramApi = paramApi;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ==================== 缓存 ====================

    /** 启动、数据恢复后从数据库载入每个汇率对的最近报价 */
    @EventListener({ApplicationReadyEvent.class, DataRestoredEvent.class})
    public synchronized void loadCache() {
        cache.clear();
        for (FxPair p : FxPair.values()) {
            FxQuoteDO q = quoteMapper.selectLatest(p.name());
            if (q != null) cache.put(p, new FxQuoteDTO(p, q.getRate(), q.getPublishTime(), q.getFetchedAt(), false));
        }
        if (lastSuccessAt == null) lastSuccessAt = cache.values().stream().map(FxQuoteDTO::fetchedAt).max(LocalDateTime::compareTo).orElse(null);
    }

    private boolean fresh(LocalDateTime now) {
        return lastSuccessAt != null && lastSuccessAt.plus(CACHE_TTL).isAfter(now);
    }

    @Override
    public Optional<FxQuoteDTO> latest(FxPair pair) {
        FxQuoteDTO q;
        synchronized (this) {
            q = cache.get(pair);
        }
        if (q == null) return Optional.empty();
        boolean stale = !q.fetchedAt().plus(CACHE_TTL).isAfter(LocalDateTime.now(clock));
        return Optional.of(new FxQuoteDTO(q.pair(), q.rate(), q.publishTime(), q.fetchedAt(), stale));
    }

    @Override
    public Optional<BigDecimal> dailyAverage(FxPair pair, LocalDate date) {
        return Optional.ofNullable(dailyMapper.selectByKey(pair.name(), date)).map(FxDailyRateDO::getAvgRate);
    }

    @Override
    public Optional<BigDecimal> monthlyAverage(FxPair pair, YearMonth month) {
        return Optional.ofNullable(monthlyMapper.selectByKey(pair.name(), month.toString())).map(FxMonthlyRateDO::getAvgRate);
    }

    // ==================== 取数 ====================

    /**
     * 取得最新报价。force 为 false 时缓存未过期（15 分钟内成功过）直接返回；失败时抛出异常，由轮询器退避重试。
     *
     * @return true 取得了新数据，false 使用缓存
     */
    public boolean refresh(boolean force) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!force && fresh(now)) return false;
        if (!busy.compareAndSet(false, true)) throw new BizException(FxErrorCodes.FETCH_BUSY);
        lastAttemptAt = now;
        try {
            Map<FxPair, Quote> pairs;
            try {
                pairs = toPairs(source.fetch());
            } catch (Exception e) {
                consecutiveFailures++;
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("[实时汇率] 获取中国银行汇率失败（第 {} 次）：{}", consecutiveFailures, lastError);
                throw BizException.of(FxErrorCodes.FETCH_FAILED, lastError);
            }
            List<FxRateUpdatedEvent> events = new ArrayList<>();
            // 事件在事务内发布：AFTER_COMMIT 监听器在提交后收到
            tx.executeWithoutResult(s -> {
                pairs.forEach((pair, q) -> save(pair, q, now, events));
                events.forEach(eventPublisher::publish);
            });
            synchronized (this) {
                pairs.forEach((pair, q) -> cache.put(pair, new FxQuoteDTO(pair, q.rate(), q.publishTime(), now, false)));
            }
            lastSuccessAt = now;
            lastError = null;
            consecutiveFailures = 0;
            return true;
        } finally {
            busy.set(false);
        }
    }

    /** USD、EUR 对人民币；EUR_USD = EUR_CNY ÷ USD_CNY（R01） */
    static Map<FxPair, Quote> toPairs(Map<String, Quote> quotes) {
        Quote usd = quotes.get("USD");
        Quote eur = quotes.get("EUR");
        if (usd == null || eur == null) throw new IllegalStateException("缺少 USD 或 EUR 报价");
        Map<FxPair, Quote> map = new EnumMap<>(FxPair.class);
        map.put(FxPair.USD_CNY, usd);
        map.put(FxPair.EUR_CNY, eur);
        LocalDateTime t = usd.publishTime().isAfter(eur.publishTime()) ? usd.publishTime() : eur.publishTime();
        map.put(FxPair.EUR_USD, new Quote("EUR", eur.rate().divide(usd.rate(), Decimals.PRICE_SCALE, RoundingMode.HALF_UP), t));
        return map;
    }

    /** 同一发布时间的报价只记一次；更新当天的日平均汇率（未结算），推送日汇率 */
    private void save(FxPair pair, Quote q, LocalDateTime now, List<FxRateUpdatedEvent> events) {
        if (quoteMapper.exists(pair.name(), q.publishTime())) return;
        FxQuoteDO d = new FxQuoteDO();
        d.setId(IdWorker.getId());
        d.setPair(pair.name());
        d.setRate(Decimals.price(q.rate()));
        d.setQuoteDate(q.publishTime().toLocalDate());
        d.setPublishTime(q.publishTime());
        d.setFetchedAt(now);
        d.setSource("BOC");
        quoteMapper.insert(d);
        events.add(new FxRateUpdatedEvent(pair, Kind.QUOTE, d.getQuoteDate(), d.getRate(), false));
        FxDailyRateDO daily = computeDaily(pair, d.getQuoteDate(), false);
        if (daily != null) events.add(new FxRateUpdatedEvent(pair, Kind.DAILY, daily.getRateDate(), daily.getAvgRate(), false));
    }

    // ==================== 日 / 月平均 ====================

    /** 按当天全部报价重算日平均汇率并推送系统日汇率 */
    private FxDailyRateDO computeDaily(FxPair pair, LocalDate date, boolean finalize) {
        List<FxQuoteDO> list = quoteMapper.selectByDate(pair.name(), date);
        if (list.isEmpty()) return null;
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        BigDecimal max = null;
        for (FxQuoteDO q : list) {
            sum = sum.add(q.getRate());
            min = min == null || q.getRate().compareTo(min) < 0 ? q.getRate() : min;
            max = max == null || q.getRate().compareTo(max) > 0 ? q.getRate() : max;
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(list.size()), Decimals.PRICE_SCALE, RoundingMode.HALF_UP);
        FxDailyRateDO d = dailyMapper.selectByKey(pair.name(), date);
        boolean isNew = d == null;
        if (isNew) {
            d = new FxDailyRateDO();
            d.setId(IdWorker.getId());
            d.setPair(pair.name());
            d.setRateDate(date);
        }
        d.setAvgRate(avg);
        d.setMinRate(min);
        d.setMaxRate(max);
        d.setSampleCount(list.size());
        d.setFinalized(finalize || Boolean.TRUE.equals(d.getFinalized()));
        if (isNew) dailyMapper.insert(d);
        else dailyMapper.updateByIdOrFail(d);
        push(pair, RateType.DAILY, date, avg, (d.getFinalized() ? "中国银行现汇买入价日平均（" : "中国银行现汇买入价当日平均（截至 ")
                + (d.getFinalized() ? list.size() + " 笔）" : list.get(list.size() - 1).getPublishTime().toLocalTime() + "，" + list.size() + " 笔）"));
        return d;
    }

    /** USD_CNY / EUR_CNY 且本位币为人民币时写入系统汇率表（R05） */
    private void push(FxPair pair, RateType type, LocalDate date, BigDecimal rate, String remark) {
        if (!CNY.equals(pair.to()) || !CNY.equals(currencyApi.getBaseCurrency())) return;
        currencyApi.saveAutoRate(pair.from(), type, date, rate, remark.length() > 128 ? remark.substring(0, 128) : remark);
    }

    /**
     * 每天 00:05：结算前一天（及上上月 1 日以来漏掉的）日平均汇率；月初结算上月（及上上月，若漏掉）的月平均汇率；删除 3 年前的数据（R03、R04、R06）。
     */
    @ErpJob(code = "FX_SETTLE", name = "汇率日 / 月平均结算", cron = "0 5 0 * * ?")
    public String settleJob() {
        return settle(LocalDate.now(clock));
    }

    public String settle(LocalDate today) {
        List<FxRateUpdatedEvent> events = new ArrayList<>();
        int[] counts = new int[3];
        tx.executeWithoutResult(s -> {
            // 日平均：今天以前、还未结算的日期（回看到上上月 1 日，保证结算月平均前当月各日已结算）
            Set<String> keys = new TreeSet<>();
            for (FxQuoteDO q : quoteMapper.selectSince(YearMonth.from(today).minusMonths(2).atDay(1))) {
                if (q.getQuoteDate().isBefore(today)) keys.add(q.getPair() + "|" + q.getQuoteDate());
            }
            for (String k : keys) {
                FxPair pair = FxPair.valueOf(k.substring(0, k.indexOf('|')));
                LocalDate date = LocalDate.parse(k.substring(k.indexOf('|') + 1));
                FxDailyRateDO existing = dailyMapper.selectByKey(pair.name(), date);
                if (existing != null && Boolean.TRUE.equals(existing.getFinalized())) continue;
                FxDailyRateDO d = computeDaily(pair, date, true);
                if (d != null) {
                    counts[0]++;
                    events.add(new FxRateUpdatedEvent(pair, Kind.DAILY, date, d.getAvgRate(), true));
                }
            }
            // 月平均：已结束的上月、上上月
            YearMonth current = YearMonth.from(today);
            for (YearMonth m : List.of(current.minusMonths(2), current.minusMonths(1))) {
                for (FxPair pair : FxPair.values()) {
                    if (monthlyMapper.selectByKey(pair.name(), m.toString()) != null) continue;
                    FxMonthlyRateDO mr = computeMonthly(pair, m);
                    if (mr != null) {
                        counts[1]++;
                        events.add(new FxRateUpdatedEvent(pair, Kind.MONTHLY, m.atEndOfMonth(), mr.getAvgRate(), true));
                    }
                }
            }
            // 保存 3 年
            LocalDate before = today.minusYears(KEEP_YEARS);
            counts[2] = quoteMapper.purge(before) + dailyMapper.purge(before) + monthlyMapper.purge(YearMonth.from(before).toString());
            events.forEach(eventPublisher::publish);
        });
        return "日平均 " + counts[0] + " 条，月平均 " + counts[1] + " 条，清理过期 " + counts[2] + " 条";
    }

    private FxMonthlyRateDO computeMonthly(FxPair pair, YearMonth month) {
        List<FxDailyRateDO> days = dailyMapper.selectRange(pair.name(), month.atDay(1), month.atEndOfMonth());
        if (days.isEmpty()) return null;
        BigDecimal sum = days.stream().map(FxDailyRateDO::getAvgRate).reduce(BigDecimal.ZERO, BigDecimal::add);
        FxMonthlyRateDO m = new FxMonthlyRateDO();
        m.setId(IdWorker.getId());
        m.setPair(pair.name());
        m.setRateMonth(month.toString());
        m.setAvgRate(sum.divide(BigDecimal.valueOf(days.size()), Decimals.PRICE_SCALE, RoundingMode.HALF_UP));
        m.setDayCount(days.size());
        monthlyMapper.insert(m);
        push(pair, RateType.MONTH_END, month.atEndOfMonth(), m.getAvgRate(), "中国银行现汇买入价月平均（" + days.size() + " 天）");
        return m;
    }

    // ==================== 查询 ====================

    public FxStatus status() {
        LocalDate today = LocalDate.now(clock);
        List<QuoteRow> rows = new ArrayList<>();
        for (FxPair p : FxPair.values()) {
            FxQuoteDTO q = latest(p).orElse(null);
            BigDecimal avg = dailyAverage(p, today).orElse(null);
            rows.add(new QuoteRow(p.name(), p.label(), q == null ? null : q.rate(), q == null ? null : q.publishTime(),
                    q == null ? null : q.fetchedAt(), q == null || q.stale(), avg));
        }
        String base = currencyApi.getBaseCurrency();
        return new FxStatus(paramApi.getBool(FxModuleConfig.P_ENABLED), polling, lastSuccessAt, lastAttemptAt, lastError, consecutiveFailures,
                nextRunAt, CNY.equals(base) ? base : null, rows);
    }

    public List<DailyRow> daily(String pair, LocalDate from, LocalDate to) {
        return dailyMapper.selectRange(pair, from, to).stream().map(d -> new DailyRow(d.getPair(), d.getRateDate(), d.getAvgRate(), d.getMinRate(),
                d.getMaxRate(), d.getSampleCount(), Boolean.TRUE.equals(d.getFinalized()))).toList();
    }

    public List<MonthlyRow> monthly(int limit) {
        return monthlyMapper.selectRecent(Math.max(1, Math.min(limit, 200))).stream()
                .map(m -> new MonthlyRow(m.getPair(), m.getRateMonth(), m.getAvgRate(), m.getDayCount())).toList();
    }

    // ==================== 轮询状态（FxPoller 维护） ====================

    void pollState(boolean active, LocalDateTime next) {
        this.polling = active;
        this.nextRunAt = next;
    }

    int consecutiveFailures() {
        return consecutiveFailures;
    }

    boolean enabled() {
        return paramApi.getBool(FxModuleConfig.P_ENABLED);
    }
}
