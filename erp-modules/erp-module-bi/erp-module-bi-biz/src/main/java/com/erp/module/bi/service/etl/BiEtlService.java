package com.erp.module.bi.service.etl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.mybatis.BaseDO;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.bi.api.BiErrorCodes;
import com.erp.module.bi.api.fact.BiFactProvider;
import com.erp.module.bi.dal.dataobject.BiAggFinanceDO;
import com.erp.module.bi.dal.dataobject.BiAggInventoryMonthlyDO;
import com.erp.module.bi.dal.dataobject.BiAggInventorySnapshotDO;
import com.erp.module.bi.dal.dataobject.BiAggProductionDO;
import com.erp.module.bi.dal.dataobject.BiAggPurchaseDO;
import com.erp.module.bi.dal.dataobject.BiAggQualityDO;
import com.erp.module.bi.dal.dataobject.BiAggSalesDO;
import com.erp.module.bi.dal.dataobject.BiEtlJobDO;
import com.erp.module.bi.dal.dataobject.BiEtlLogDO;
import com.erp.module.bi.dal.mapper.BiAggFinanceMapper;
import com.erp.module.bi.dal.mapper.BiAggInventoryMonthlyMapper;
import com.erp.module.bi.dal.mapper.BiAggInventorySnapshotMapper;
import com.erp.module.bi.dal.mapper.BiAggProductionMapper;
import com.erp.module.bi.dal.mapper.BiAggPurchaseMapper;
import com.erp.module.bi.dal.mapper.BiAggQualityMapper;
import com.erp.module.bi.dal.mapper.BiAggSalesMapper;
import com.erp.module.bi.dal.mapper.BiEtlJobMapper;
import com.erp.module.bi.dal.mapper.BiEtlLogMapper;
import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.finance.api.query.CostQueryApi;
import com.erp.module.purchase.api.supplier.SupplierApi;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.user.UserApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 汇总数据任务（需求 13-01 BI-DATA-R01 / R02）。
 * <ul>
 *   <li>增量处理器：每 10 分钟重算最近区间（本月与最近 7 天、当前期间往来与库存流水、当日库存快照），数据延迟 ≤ 15 分钟；</li>
 *   <li>全量校对：重算最近 3 个月，与现有汇总行逐行比较，差异行覆盖并计数；</li>
 *   <li>库存日快照：每日 23:50 记录当日库存。</li>
 * </ul>
 * 事实数据来自各业务模块的 {@link BiFactProvider}，运行时跳过数据权限；同一时刻只运行一个任务（避免重算区间互相覆盖）。
 */
@Slf4j
@Service
public class BiEtlService {

    public enum Job {
        INCREMENTAL("增量处理器"),
        RECON_SALES("销售日汇总全量校对"),
        RECON_PURCHASE("采购日汇总全量校对"),
        RECON_PRODUCTION("生产日汇总全量校对"),
        RECON_QUALITY("品质日汇总全量校对"),
        RECON_INVENTORY("库存汇总全量校对"),
        RECON_FINANCE("往来月汇总全量校对"),
        INV_SNAPSHOT("库存日快照");

        public final String label;

        Job(String label) {
            this.label = label;
        }
    }

    public static final String IDLE = "IDLE";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    /** 全量校对的月数（含本月） */
    static final int RECON_MONTHS = 3;

    /** 区间统计：处理行数、差异行数 */
    public record Stat(int rows, int diff) {
        Stat plus(Stat o) {
            return new Stat(rows + o.rows, diff + o.diff);
        }

        static final Stat ZERO = new Stat(0, 0);
    }

    private final ObjectProvider<BiFactProvider> providers;
    private final BiAggSalesMapper salesMapper;
    private final BiAggPurchaseMapper purchaseMapper;
    private final BiAggProductionMapper productionMapper;
    private final BiAggQualityMapper qualityMapper;
    private final BiAggInventorySnapshotMapper snapshotMapper;
    private final BiAggInventoryMonthlyMapper monthlyMapper;
    private final BiAggFinanceMapper financeMapper;
    private final BiEtlJobMapper jobMapper;
    private final BiEtlLogMapper logMapper;
    private final MaterialApi materialApi;
    private final CustomerApi customerApi;
    private final SupplierApi supplierApi;
    private final UserApi userApi;
    private final OrgApi orgApi;
    private final CostQueryApi costQueryApi;
    private final TransactionTemplate tx;
    private final ReentrantLock lock = new ReentrantLock();

    public BiEtlService(ObjectProvider<BiFactProvider> providers, BiAggSalesMapper salesMapper, BiAggPurchaseMapper purchaseMapper,
                        BiAggProductionMapper productionMapper, BiAggQualityMapper qualityMapper, BiAggInventorySnapshotMapper snapshotMapper,
                        BiAggInventoryMonthlyMapper monthlyMapper, BiAggFinanceMapper financeMapper, BiEtlJobMapper jobMapper, BiEtlLogMapper logMapper,
                        MaterialApi materialApi, CustomerApi customerApi, SupplierApi supplierApi, UserApi userApi, OrgApi orgApi,
                        CostQueryApi costQueryApi, PlatformTransactionManager txManager) {
        this.providers = providers;
        this.salesMapper = salesMapper;
        this.purchaseMapper = purchaseMapper;
        this.productionMapper = productionMapper;
        this.qualityMapper = qualityMapper;
        this.snapshotMapper = snapshotMapper;
        this.monthlyMapper = monthlyMapper;
        this.financeMapper = financeMapper;
        this.jobMapper = jobMapper;
        this.logMapper = logMapper;
        this.materialApi = materialApi;
        this.customerApi = customerApi;
        this.supplierApi = supplierApi;
        this.userApi = userApi;
        this.orgApi = orgApi;
        this.costQueryApi = costQueryApi;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ==================== 任务列表与运行 ====================

    /** 全部数据任务（缺失的任务行自动补齐） */
    public List<BiEtlJobDO> listJobs() {
        Map<String, BiEtlJobDO> existing = new HashMap<>();
        jobMapper.selectList(null).forEach(j -> existing.put(j.getCode(), j));
        List<BiEtlJobDO> list = new ArrayList<>();
        for (Job j : Job.values()) list.add(existing.containsKey(j.name()) ? existing.get(j.name()) : ensureJob(j));
        return list;
    }

    public List<BiEtlLogDO> listLogs(String jobCode, int limit) {
        return logMapper.selectList(new LambdaQueryWrapper<BiEtlLogDO>().eq(jobCode != null && !jobCode.isBlank(), BiEtlLogDO::getJobCode, jobCode)
                .orderByDesc(BiEtlLogDO::getStartedAt).last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    /** 最近一次成功处理时间（BI-DATA-R04：报表右上角“数据更新于”） */
    public LocalDateTime lastSuccessAt() {
        return jobMapper.selectList(new LambdaQueryWrapper<BiEtlJobDO>().eq(BiEtlJobDO::getLastResult, SUCCESS)).stream()
                .map(BiEtlJobDO::getLastFinishedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }

    /** 立即运行（页面）：已有任务在运行时提示；运行失败记录在任务与日志中，返回任务最新状态 */
    public BiEtlJobDO runNow(String code) {
        Job job;
        try {
            job = Job.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw BizException.of(BiErrorCodes.NOT_EXISTS, "数据任务");
        }
        if (!lock.tryLock()) {
            throw BizException.of(BiErrorCodes.ETL_RUNNING, currentRunning());
        }
        try {
            execute(job);
        } catch (IllegalStateException e) {
            // 失败原因已写入 bi_etl_job / bi_etl_log
        } finally {
            lock.unlock();
        }
        return ensureJob(job);
    }

    /** 定时任务调用：等待其他数据任务结束后运行 */
    public Stat runScheduled(Job job) {
        try {
            if (!lock.tryLock(30, TimeUnit.MINUTES)) {
                throw BizException.of(BiErrorCodes.ETL_RUNNING, currentRunning());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.of(BiErrorCodes.ETL_RUNNING, currentRunning());
        }
        try {
            return execute(job);
        } finally {
            lock.unlock();
        }
    }

    private String currentRunning() {
        return jobMapper.selectList(new LambdaQueryWrapper<BiEtlJobDO>().eq(BiEtlJobDO::getJobStatus, RUNNING)).stream()
                .map(BiEtlJobDO::getName).findFirst().orElse("数据任务");
    }

    private Stat execute(Job job) {
        BiEtlJobDO row = ensureJob(job);
        LocalDateTime started = LocalDateTime.now();
        row.setJobStatus(RUNNING);
        row.setLastStartedAt(started);
        jobMapper.updateByIdOrFail(row);
        long t0 = System.currentTimeMillis();
        Stat stat = null;
        String error = null;
        try {
            stat = DataScopes.ignore(() -> doRun(job, LocalDate.now()));
        } catch (RuntimeException e) {
            log.error("BI 数据任务 {} 失败", job, e);
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        BiEtlJobDO fresh = jobMapper.selectById(row.getId());
        fresh.setJobStatus(IDLE);
        fresh.setLastFinishedAt(LocalDateTime.now());
        fresh.setLastDurationMs(System.currentTimeMillis() - t0);
        fresh.setLastResult(error == null ? SUCCESS : FAILED);
        fresh.setLastRows(stat == null ? 0 : stat.rows());
        fresh.setLastDiffRows(stat == null ? 0 : stat.diff());
        fresh.setLastMessage(error == null ? null : truncate(error, 1000));
        jobMapper.updateByIdOrFail(fresh);
        BiEtlLogDO logRow = new BiEtlLogDO();
        logRow.setJobCode(job.name());
        logRow.setStartedAt(started);
        logRow.setFinishedAt(fresh.getLastFinishedAt());
        logRow.setResult(fresh.getLastResult());
        logRow.setRowCount(fresh.getLastRows());
        logRow.setDiffRows(fresh.getLastDiffRows());
        logRow.setMessage(error == null ? null : truncate(error, 2000));
        logMapper.insert(logRow);
        if (error != null) throw new IllegalStateException(error);
        return stat;
    }

    private BiEtlJobDO ensureJob(Job job) {
        BiEtlJobDO row = jobMapper.selectOne(new LambdaQueryWrapper<BiEtlJobDO>().eq(BiEtlJobDO::getCode, job.name()));
        if (row != null) return row;
        row = new BiEtlJobDO();
        row.setCode(job.name());
        row.setName(job.label);
        row.setJobStatus(IDLE);
        jobMapper.insert(row);
        return jobMapper.selectById(row.getId());
    }

    Stat doRun(Job job, LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = YearMonth.from(today).atEndOfMonth();
        LocalDate recentFrom = monthStart.isBefore(today.minusDays(7)) ? monthStart : today.minusDays(7);
        LocalDate reconFrom = monthStart.minusMonths(RECON_MONTHS - 1);
        String curPeriod = BiAggBuilder.period(today);
        String reconPeriod = BiAggBuilder.period(reconFrom);
        BiAggBuilder b = builder();
        return switch (job) {
            case INCREMENTAL -> refreshSales(b, recentFrom, monthEnd)
                    .plus(refreshPurchase(b, recentFrom, monthEnd))
                    .plus(refreshProduction(b, recentFrom, monthEnd))
                    .plus(refreshQuality(b, recentFrom, monthEnd))
                    .plus(refreshFinance(b, BiAggBuilder.period(recentFrom), curPeriod))
                    .plus(refreshInventoryMonthly(b, BiAggBuilder.period(recentFrom), curPeriod))
                    .plus(refreshSnapshot(b, today));
            case RECON_SALES -> refreshSales(b, reconFrom, monthEnd);
            case RECON_PURCHASE -> refreshPurchase(b, reconFrom, monthEnd);
            case RECON_PRODUCTION -> refreshProduction(b, reconFrom, monthEnd);
            case RECON_QUALITY -> refreshQuality(b, reconFrom, monthEnd);
            case RECON_INVENTORY -> refreshInventoryMonthly(b, reconPeriod, curPeriod).plus(refreshSnapshot(b, today));
            case RECON_FINANCE -> refreshFinance(b, reconPeriod, curPeriod);
            case INV_SNAPSHOT -> refreshSnapshot(b, today);
        };
    }

    private BiAggBuilder builder() {
        BiLookup lookup = new BiLookup(materialApi, customerApi, supplierApi, userApi, orgApi);
        Map<String, Optional<BigDecimal>> costs = new HashMap<>();
        return new BiAggBuilder(lookup, (mat, period) -> costs.computeIfAbsent(mat + "|" + period, k -> costQueryApi.getUnitCost(mat, period)));
    }

    // ==================== 各汇总表重算 ====================

    Stat refreshSales(BiAggBuilder b, LocalDate from, LocalDate to) {
        List<BiAggSalesDO> rows = b.sales(collect(p -> p.salesFacts(from, to)));
        return replace(rows, () -> salesMapper.selectList(new LambdaQueryWrapper<BiAggSalesDO>().between(BiAggSalesDO::getStatDate, from, to)),
                BiAggBuilder::salesKey, BiAggBuilder::salesValues, () -> salesMapper.deleteRange(from, to), salesMapper);
    }

    Stat refreshPurchase(BiAggBuilder b, LocalDate from, LocalDate to) {
        List<BiAggPurchaseDO> rows = b.purchase(collect(p -> p.purchaseFacts(from, to)));
        return replace(rows, () -> purchaseMapper.selectList(new LambdaQueryWrapper<BiAggPurchaseDO>().between(BiAggPurchaseDO::getStatDate, from, to)),
                BiAggBuilder::purchaseKey, BiAggBuilder::purchaseValues, () -> purchaseMapper.deleteRange(from, to), purchaseMapper);
    }

    Stat refreshProduction(BiAggBuilder b, LocalDate from, LocalDate to) {
        List<BiAggProductionDO> rows = b.production(collect(p -> p.productionFacts(from, to)));
        return replace(rows, () -> productionMapper.selectList(new LambdaQueryWrapper<BiAggProductionDO>().between(BiAggProductionDO::getStatDate, from, to)),
                BiAggBuilder::productionKey, BiAggBuilder::productionValues, () -> productionMapper.deleteRange(from, to), productionMapper);
    }

    Stat refreshQuality(BiAggBuilder b, LocalDate from, LocalDate to) {
        List<BiAggQualityDO> rows = b.quality(collect(p -> p.qualityFacts(from, to)));
        return replace(rows, () -> qualityMapper.selectList(new LambdaQueryWrapper<BiAggQualityDO>().between(BiAggQualityDO::getStatDate, from, to)),
                BiAggBuilder::qualityKey, BiAggBuilder::qualityValues, () -> qualityMapper.deleteRange(from, to), qualityMapper);
    }

    Stat refreshSnapshot(BiAggBuilder b, LocalDate date) {
        List<BiAggInventorySnapshotDO> rows = b.snapshot(date, collect(BiFactProvider::inventorySnapshot));
        return replace(rows, () -> snapshotMapper.selectList(new LambdaQueryWrapper<BiAggInventorySnapshotDO>().eq(BiAggInventorySnapshotDO::getStatDate, date)),
                BiAggBuilder::snapshotKey, BiAggBuilder::snapshotValues, () -> snapshotMapper.deleteRange(date, date), snapshotMapper);
    }

    Stat refreshInventoryMonthly(BiAggBuilder b, String fromPeriod, String toPeriod) {
        List<BiAggInventoryMonthlyDO> rows = b.inventoryMonthly(collect(p -> p.inventoryFlows(fromPeriod, toPeriod)));
        return replace(rows, () -> monthlyMapper.selectList(new LambdaQueryWrapper<BiAggInventoryMonthlyDO>()
                        .between(BiAggInventoryMonthlyDO::getPeriod, fromPeriod, toPeriod)),
                BiAggBuilder::monthlyKey, BiAggBuilder::monthlyValues, () -> monthlyMapper.deleteRange(fromPeriod, toPeriod), monthlyMapper);
    }

    Stat refreshFinance(BiAggBuilder b, String fromPeriod, String toPeriod) {
        List<BiAggFinanceDO> rows = new ArrayList<>();
        YearMonth from = YearMonth.parse(fromPeriod, BiAggBuilder.PERIOD);
        YearMonth to = YearMonth.parse(toPeriod, BiAggBuilder.PERIOD);
        for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
            String period = ym.format(BiAggBuilder.PERIOD);
            rows.addAll(b.finance(collect(p -> p.financeFacts(period))));
        }
        return replace(rows, () -> financeMapper.selectList(new LambdaQueryWrapper<BiAggFinanceDO>().between(BiAggFinanceDO::getPeriod, fromPeriod, toPeriod)),
                BiAggBuilder::financeKey, BiAggBuilder::financeValues, () -> financeMapper.deleteRange(fromPeriod, toPeriod), financeMapper);
    }

    private <F> List<F> collect(Function<BiFactProvider, List<F>> fn) {
        List<F> all = new ArrayList<>();
        providers.orderedStream().forEach(p -> all.addAll(fn.apply(p)));
        return all;
    }

    /**
     * 与现有汇总行逐行比较（按粒度键）：值不同、缺失或多余的键计为差异行；有差异时在一个事务内清除区间并写入重算结果。
     */
    <T extends BaseDO> Stat replace(List<T> computed, Supplier<List<T>> existingLoader, Function<T, String> key, Function<T, String> values,
                                    Runnable deleteRange, BaseMapperX<T> mapper) {
        Map<String, String> want = new HashMap<>();
        for (T r : computed) want.put(key.apply(r), values.apply(r));
        Map<String, String> have = new HashMap<>();
        int duplicate = 0;
        for (T r : existingLoader.get()) {
            if (have.put(key.apply(r), values.apply(r)) != null) duplicate++;
        }
        Set<String> keys = new HashSet<>(want.keySet());
        keys.addAll(have.keySet());
        int diff = duplicate;
        for (String k : keys) {
            if (!Objects.equals(want.get(k), have.get(k))) diff++;
        }
        if (diff > 0) {
            tx.executeWithoutResult(s -> {
                deleteRange.run();
                for (T r : computed) mapper.insert(r);
            });
        }
        return new Stat(computed.size(), diff);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
