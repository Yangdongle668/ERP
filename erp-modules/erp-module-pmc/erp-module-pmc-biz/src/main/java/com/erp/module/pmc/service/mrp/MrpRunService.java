package com.erp.module.pmc.service.mrp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.pmc.api.PmcErrorCodes;
import com.erp.module.pmc.api.mrp.MrpRunCompletedEvent;
import com.erp.module.pmc.config.PmcModuleConfig;
import com.erp.module.pmc.controller.vo.MrpVOs.RunQuery;
import com.erp.module.pmc.controller.vo.MrpVOs.RunReq;
import com.erp.module.pmc.controller.vo.MrpVOs.RunRow;
import com.erp.module.pmc.dal.dataobject.PmcMrpBalanceDO;
import com.erp.module.pmc.dal.dataobject.PmcMrpExceptionDO;
import com.erp.module.pmc.dal.dataobject.PmcMrpFingerprintDO;
import com.erp.module.pmc.dal.dataobject.PmcMrpPeggingDO;
import com.erp.module.pmc.dal.dataobject.PmcMrpResultDO;
import com.erp.module.pmc.dal.dataobject.PmcMrpRunDO;
import com.erp.module.pmc.dal.mapper.PmcMrpBalanceMapper;
import com.erp.module.pmc.dal.mapper.PmcMrpExceptionMapper;
import com.erp.module.pmc.dal.mapper.PmcMrpFingerprintMapper;
import com.erp.module.pmc.dal.mapper.PmcMrpPeggingMapper;
import com.erp.module.pmc.dal.mapper.PmcMrpResultMapper;
import com.erp.module.pmc.dal.mapper.PmcMrpRunMapper;
import com.erp.module.pmc.service.PlanningData;
import com.erp.module.pmc.service.PmcSupport;
import com.erp.module.pmc.service.mrp.MrpModel.Balance;
import com.erp.module.pmc.service.mrp.MrpModel.Mat;
import com.erp.module.pmc.service.mrp.MrpModel.Output;
import com.erp.module.pmc.service.mrp.MrpModel.Peg;
import com.erp.module.pmc.service.mrp.MrpModel.Planned;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.task.AsyncTaskApi;
import com.erp.module.system.api.user.UserDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MRP 运算控制（需求 06-03 第 3.3 节）：同一时间只允许一个运算；后台任务执行；成功后上一次运算的待处理建议置为“被替代”；
 * 失败记录原因且不影响上一次的建议；运行超过 1 小时的运算视为中断。
 *
 * <p>净变更（NET_CHANGE）：仍在内存中完整计算，再按物料比较本次与上一次运算的结果指纹，只有结果变化的物料生成新建议、例外和供需平衡；
 * 未变化物料的待处理建议（含计划员的修改）、例外和供需平衡复制到本次运算，已忽略的建议不会重新出现。
 * 上一次运算为指定订单运算、没有指纹或运算参数不同时按全量处理（运算参数中记录原因）。
 */
@Service("pmcMrpRunService")
public class MrpRunService {

    private static final Logger LOG = LoggerFactory.getLogger(MrpRunService.class);
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    private static final Map<String, Integer> BALANCE_ORDER = Map.of("OPENING", 0, "QC", 1, "PURCHASE", 1, "WIP", 1, "SUBSTITUTE", 1, "PLANNED", 2);

    private final PmcMrpRunMapper runMapper;
    private final PmcMrpResultMapper resultMapper;
    private final PmcMrpPeggingMapper peggingMapper;
    private final PmcMrpExceptionMapper exceptionMapper;
    private final PmcMrpBalanceMapper balanceMapper;
    private final PmcMrpFingerprintMapper fingerprintMapper;
    private final MrpInputLoader loader;
    private final PmcSupport support;
    private final PlanningData data;
    private final AsyncTaskApi asyncTaskApi;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    public MrpRunService(PmcMrpRunMapper runMapper, PmcMrpResultMapper resultMapper, PmcMrpPeggingMapper peggingMapper,
                         PmcMrpExceptionMapper exceptionMapper, PmcMrpBalanceMapper balanceMapper, PmcMrpFingerprintMapper fingerprintMapper,
                         MrpInputLoader loader, PmcSupport support,
                         PlanningData data, AsyncTaskApi asyncTaskApi, DomainEventPublisher eventPublisher, TransactionTemplate tx,
                         ObjectMapper objectMapper) {
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.peggingMapper = peggingMapper;
        this.exceptionMapper = exceptionMapper;
        this.balanceMapper = balanceMapper;
        this.fingerprintMapper = fingerprintMapper;
        this.loader = loader;
        this.support = support;
        this.data = data;
        this.asyncTaskApi = asyncTaskApi;
        this.eventPublisher = eventPublisher;
        this.tx = tx;
        this.objectMapper = objectMapper;
    }

    // ==================== 发起 ====================

    /** 发起运算（后台任务），返回运算记录 ID */
    public synchronized Long start(RunReq req) {
        MrpInputLoader.Options o = options(req);
        PmcMrpRunDO run = tx.execute(s -> create(o));
        Long taskId = asyncTaskApi.submit("MRP", "MRP 运算 " + run.getRunNo(), PmcModuleConfig.MODULE, ctx -> {
            execute(run.getId(), o, ctx::progress);
            ctx.resultMessage("MRP 运算完成：" + runMapper.selectById(run.getId()).getSuggestionCount() + " 条建议");
        });
        runMapper.update(null, new LambdaUpdateWrapper<PmcMrpRunDO>().set(PmcMrpRunDO::getTaskId, taskId).eq(PmcMrpRunDO::getId, run.getId()));
        return run.getId();
    }

    /** 夜间全量运算（参数 pmc.mrp.nightly 为是时） */
    @ErpJob(code = "PMC_MRP_NIGHTLY", name = "MRP 夜间全量运算", cron = "0 30 2 * * ?")
    public String nightly() {
        if (!support.params().getBool(PmcModuleConfig.P_NIGHTLY)) return "未开启夜间运算";
        MrpInputLoader.Options o = options(new RunReq("FULL", null, null, null, null, null));
        PmcMrpRunDO run;
        synchronized (this) {
            run = tx.execute(s -> create(o));
        }
        execute(run.getId(), o, p -> { });
        PmcMrpRunDO r = runMapper.selectById(run.getId());
        return r.getRunNo() + " " + r.getRunStatus() + "，建议 " + r.getSuggestionCount() + " 条";
    }

    private MrpInputLoader.Options options(RunReq req) {
        ParamApi p = support.params();
        String type = req == null || !StringUtils.hasText(req.runType()) ? "FULL" : req.runType();
        if (!List.of("FULL", "NET_CHANGE", "ORDER").contains(type)) type = "FULL";
        Set<Long> lines = req == null || req.orderLineIds() == null ? Set.of() : new HashSet<>(req.orderLineIds());
        if ("ORDER".equals(type) && lines.isEmpty()) throw new BizException(PmcErrorCodes.MRP_ORDER_SCOPE_REQUIRED);
        int horizon = req != null && req.horizonDays() != null && req.horizonDays() > 0 ? Math.min(req.horizonDays(), 730) : p.getInt(PmcModuleConfig.P_HORIZON);
        boolean forecast = req != null && req.includeForecast() != null ? req.includeForecast() : p.getBool(PmcModuleConfig.P_INCLUDE_FORECAST);
        boolean safety = req != null && req.includeSafety() != null ? req.includeSafety() : p.getBool(PmcModuleConfig.P_INCLUDE_SAFETY);
        boolean substitute = req != null && req.useSubstitute() != null ? req.useSubstitute() : p.getBool(PmcModuleConfig.P_USE_SUBSTITUTE);
        return new MrpInputLoader.Options(type, lines, horizon, forecast, safety, p.getBool(PmcModuleConfig.P_USE_MPS), p.getInt(PmcModuleConfig.P_TOLERANCE),
                substitute);
    }

    private PmcMrpRunDO create(MrpInputLoader.Options o) {
        PmcMrpRunDO running = runMapper.selectOne(new LambdaQueryWrapper<PmcMrpRunDO>().eq(PmcMrpRunDO::getRunStatus, RUNNING).last("LIMIT 1"));
        if (running != null) {
            if (running.getStartedAt().isBefore(LocalDateTime.now().minusHours(1))) {
                fail(running.getId(), "运算中断");
            } else {
                throw BizException.of(PmcErrorCodes.MRP_RUNNING, running.getRunNo(), support.userName(running.getOperatorId()),
                        running.getStartedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
            }
        }
        PmcMrpRunDO run = new PmcMrpRunDO();
        run.setRunNo(support.nextNo(PmcModuleConfig.MRP_RUN));
        run.setRunType(o.runType());
        run.setScope(json(Map.of("orderLineIds", o.orderLineIds())));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("horizonDays", o.horizonDays());
        params.put("includeForecast", o.includeForecast());
        params.put("includeSafety", o.includeSafety());
        params.put("useMps", o.useMps());
        params.put("toleranceDays", o.toleranceDays());
        params.put("useSubstitute", o.useSubstitute());
        run.setParams(json(params));
        run.setRunStatus(RUNNING);
        run.setStartedAt(LocalDateTime.now());
        run.setMaterialCount(0);
        run.setSuggestionCount(0);
        run.setExceptionCount(0);
        run.setProgress(0);
        run.setOperatorId(support.currentUser());
        runMapper.insert(run);
        return run;
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // ==================== 执行 ====================

    interface Progress {
        void report(int percent);
    }

    void execute(Long runId, MrpInputLoader.Options o, Progress progress) {
        try {
            MrpModel.Input in = loader.load(o);
            progress.report(30);
            Output out = MrpEngine.run(in);
            progress.report(60);
            tx.executeWithoutResult(s -> persist(runId, in, out, o));
            progress.report(100);
        } catch (RuntimeException ex) {
            LOG.warn("MRP 运算失败：{}", ex.getMessage(), ex);
            String msg = ex instanceof BizException ? ex.getMessage() : "MRP 运算失败：" + ex.getMessage();
            tx.executeWithoutResult(s -> fail(runId, msg));
            throw ex;
        }
    }

    private void fail(Long runId, String msg) {
        PmcMrpRunDO r = runMapper.selectById(runId);
        r.setRunStatus(FAILED);
        r.setFinishedAt(LocalDateTime.now());
        r.setErrorMsg(msg == null ? null : msg.length() > 2000 ? msg.substring(0, 2000) : msg);
        runMapper.updateByIdOrFail(r);
        eventPublisher.publish(new MrpRunCompletedEvent(r.getId(), r.getRunNo(), FAILED, 0, 0, r.getOperatorId()));
    }

    /** 净变更比较结果：base 为空表示按全量处理（reason 为原因） */
    private record NetChange(PmcMrpRunDO base, Set<Long> changed, String reason) {
    }

    private NetChange netChange(Long runId, Map<Long, String> fingerprints) {
        PmcMrpRunDO base = runMapper.selectOne(new LambdaQueryWrapper<PmcMrpRunDO>().eq(PmcMrpRunDO::getRunStatus, SUCCESS).ne(PmcMrpRunDO::getId, runId)
                .orderByDesc(PmcMrpRunDO::getStartedAt).orderByDesc(PmcMrpRunDO::getId).last("LIMIT 1"));
        if (base == null) return new NetChange(null, Set.of(), "没有可比较的上次运算，按全量处理");
        if ("ORDER".equals(base.getRunType())) return new NetChange(null, Set.of(), "上次为指定订单运算，按全量处理");
        if (!paramsOf(base.getParams()).equals(paramsOf(runMapper.selectById(runId).getParams()))) {
            return new NetChange(null, Set.of(), "运算参数与上次（" + base.getRunNo() + "）不同，按全量处理");
        }
        Map<Long, String> old = new HashMap<>();
        for (PmcMrpFingerprintDO f : fingerprintMapper.selectList(new LambdaQueryWrapper<PmcMrpFingerprintDO>().eq(PmcMrpFingerprintDO::getRunId, base.getId()))) {
            old.put(f.getMaterialId(), f.getFingerprint());
        }
        if (old.isEmpty()) return new NetChange(null, Set.of(), "上次运算（" + base.getRunNo() + "）没有结果指纹，按全量处理");
        Set<Long> changed = new HashSet<>();
        Set<Long> keys = new HashSet<>(old.keySet());
        keys.addAll(fingerprints.keySet());
        for (Long id : keys) if (!java.util.Objects.equals(old.get(id), fingerprints.get(id))) changed.add(id);
        return new NetChange(base, changed, null);
    }

    /** 运算参数（不含净变更信息） */
    private Map<String, Object> paramsOf(String json) {
        if (json == null) return Map.of();
        try {
            Map<String, Object> m = new LinkedHashMap<>(objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            }));
            m.remove("netChange");
            return m;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    /** 复制上次运算中未变化物料的待处理建议（含追溯）、例外与供需平衡；返回“物料|需求日期|数量”→ 新建议 ID（用于子件追溯关联父件） */
    private Map<String, Long> copyUnchanged(Long runId, PmcMrpRunDO base, Set<Long> changed) {
        LocalDate today = LocalDate.now();
        Map<Long, Long> idMap = new HashMap<>();
        Map<String, Long> keys = new HashMap<>();
        for (PmcMrpResultDO r : resultMapper.selectList(new LambdaQueryWrapper<PmcMrpResultDO>().eq(PmcMrpResultDO::getRunId, base.getId())
                .eq(PmcMrpResultDO::getSuggestionStatus, SuggestionService.PENDING).orderByAsc(PmcMrpResultDO::getId))) {
            if (changed.contains(r.getMaterialId())) continue;
            Long old = r.getId();
            fresh(r);
            r.setRunId(runId);
            if (r.getReleaseDate() != null && r.getReleaseDate().isBefore(today)) {
                r.setIsLate(true);
                r.setReleaseDate(today);
            }
            resultMapper.insert(r);
            idMap.put(old, r.getId());
            keys.put(key(r.getMaterialId(), r.getOriginalRequiredDate(), r.getOriginalQty()), r.getId());
        }
        if (!idMap.isEmpty()) {
            for (PmcMrpPeggingDO g : peggingMapper.selectList(new LambdaQueryWrapper<PmcMrpPeggingDO>().in(PmcMrpPeggingDO::getResultId, idMap.keySet()))) {
                fresh(g);
                g.setRunId(runId);
                g.setResultId(idMap.get(g.getResultId()));
                g.setParentResultId(g.getParentResultId() == null ? null : idMap.get(g.getParentResultId()));
                peggingMapper.insert(g);
            }
        }
        for (PmcMrpExceptionDO e : exceptionMapper.selectList(new LambdaQueryWrapper<PmcMrpExceptionDO>().eq(PmcMrpExceptionDO::getRunId, base.getId()))) {
            if (changed.contains(e.getMaterialId())) continue;
            fresh(e);
            e.setRunId(runId);
            exceptionMapper.insert(e);
        }
        for (PmcMrpBalanceDO b : balanceMapper.selectList(new LambdaQueryWrapper<PmcMrpBalanceDO>().eq(PmcMrpBalanceDO::getRunId, base.getId()))) {
            if (changed.contains(b.getMaterialId())) continue;
            fresh(b);
            b.setRunId(runId);
            balanceMapper.insert(b);
        }
        return keys;
    }

    private static void fresh(com.erp.framework.mybatis.BaseDO d) {
        d.setId(null);
        d.setVersion(0);
        d.setCreatedAt(null);
        d.setCreatedBy(null);
        d.setUpdatedAt(null);
        d.setUpdatedBy(null);
    }

    private static String key(Long materialId, LocalDate date, BigDecimal qty) {
        return materialId + "|" + date + "|" + (qty == null ? "" : qty.stripTrailingZeros().toPlainString());
    }

    private void persist(Long runId, MrpModel.Input in, Output out, MrpInputLoader.Options o) {
        Map<Long, Mat> mats = in.mats();
        Map<Long, Long> suppliers = new HashMap<>();
        Map<Long, String> fingerprints = MrpFingerprints.of(out, mats.keySet());
        NetChange nc = "NET_CHANGE".equals(o.runType()) ? netChange(runId, fingerprints) : null;
        boolean partial = nc != null && nc.base() != null;
        Set<Long> changed = partial ? nc.changed() : Set.of();
        Map<String, Long> copied = partial ? copyUnchanged(runId, nc.base(), changed) : Map.of();
        for (Planned p : out.planned()) {
            if (partial && !changed.contains(p.materialId)) continue;
            Mat m = mats.get(p.materialId);
            PmcMrpResultDO r = new PmcMrpResultDO();
            r.setRunId(runId);
            r.setSuggestionType(p.type);
            r.setMaterialId(p.materialId);
            r.setQty(p.qty);
            r.setOriginalQty(p.qty);
            r.setNetRequirement(p.net);
            r.setRequiredDate(p.requiredDate);
            r.setOriginalRequiredDate(p.requiredDate);
            r.setReleaseDate(p.releaseDate);
            r.setIsLate(p.late);
            r.setPlannerId(m == null ? null : m.plannerId());
            r.setBuyerId(m == null ? null : m.buyerId());
            if ("MAKE".equals(p.type) || "OUTSOURCE".equals(p.type)) r.setBomId(m == null ? null : m.bomId());
            if (!"MAKE".equals(p.type)) {
                r.setSupplierId(suppliers.computeIfAbsent(p.materialId,
                        k -> data.supplierApi().getDefaultSupplier(k).map(SupplierDTO::id).orElse(null)));
            }
            r.setSuggestionStatus(SuggestionService.PENDING);
            resultMapper.insert(r);
            p.resultId = r.getId();
        }
        for (Planned p : out.planned()) {
            if (p.resultId == null) continue;
            for (Peg g : p.pegs) {
                PmcMrpPeggingDO d = new PmcMrpPeggingDO();
                d.setRunId(runId);
                d.setResultId(p.resultId);
                d.setDemandType(g.demandType());
                d.setSourceId(g.sourceId());
                d.setSourceNo(g.sourceNo());
                d.setParentMaterialId(g.parentMaterialId());
                d.setParentResultId(g.parent() == null ? null : g.parent().resultId != null ? g.parent().resultId
                        : copied.get(key(g.parent().materialId, g.parent().requiredDate, g.parent().qty)));
                d.setQty(g.qty());
                d.setRequiredDate(g.date());
                peggingMapper.insert(d);
            }
        }
        for (MrpModel.Exception e : out.exceptions()) {
            if (partial && !changed.contains(e.materialId())) continue;
            Mat m = mats.get(e.materialId());
            PmcMrpExceptionDO d = new PmcMrpExceptionDO();
            d.setRunId(runId);
            d.setMaterialId(e.materialId());
            d.setExceptionType(e.type());
            d.setDocType(e.docType());
            d.setDocId(e.docId());
            d.setDocNo(e.docNo());
            d.setDocLineId(e.lineId());
            d.setSupplyDate(e.supplyDate());
            d.setSuggestedDate(e.suggestedDate());
            d.setQty(e.qty());
            d.setMessage(e.message().length() > 256 ? e.message().substring(0, 256) : e.message());
            boolean purchase = e.docType() != null && !"MFG_PROD_ORDER".equals(e.docType());
            d.setOwnerId(m == null ? null : purchase && m.buyerId() != null ? m.buyerId() : m.plannerId());
            d.setHandled(false);
            exceptionMapper.insert(d);
        }
        Map<Long, List<Balance>> byMat = new LinkedHashMap<>();
        for (Balance b : out.balances()) {
            if (partial && !changed.contains(b.materialId())) continue;
            byMat.computeIfAbsent(b.materialId(), k -> new ArrayList<>()).add(b);
        }
        for (Map.Entry<Long, List<Balance>> e : byMat.entrySet()) {
            Mat m = mats.get(e.getKey());
            List<Balance> list = e.getValue();
            list.sort(Comparator.comparing((Balance b) -> b.date().isBefore(in.today()) ? in.today() : b.date())
                    .thenComparing(b -> BALANCE_ORDER.getOrDefault(b.type(), 3)));
            BigDecimal projected = BigDecimal.ZERO;
            int seq = 0;
            for (Balance b : list) {
                projected = projected.add(b.supply()).subtract(b.demand());
                PmcMrpBalanceDO d = new PmcMrpBalanceDO();
                d.setRunId(runId);
                d.setMaterialId(e.getKey());
                d.setSeq(++seq);
                d.setBalDate(b.date());
                d.setEntryType(b.type());
                d.setDocNo(b.docNo());
                d.setParentMaterialId(b.parentMaterialId());
                d.setDemandQty(b.demand());
                d.setSupplyQty(b.supply());
                d.setProjectedQty(projected);
                d.setSafetyStock(m == null ? BigDecimal.ZERO : PmcSupport.nz(m.safetyStock()));
                balanceMapper.insert(d);
            }
        }
        // 新运算成功：上一次运算中待处理的建议被替代
        resultMapper.update(null, new LambdaUpdateWrapper<PmcMrpResultDO>().set(PmcMrpResultDO::getSuggestionStatus, SuggestionService.SUPERSEDED)
                .ne(PmcMrpResultDO::getRunId, runId).eq(PmcMrpResultDO::getSuggestionStatus, SuggestionService.PENDING));
        for (Map.Entry<Long, String> f : fingerprints.entrySet()) {
            PmcMrpFingerprintDO d = new PmcMrpFingerprintDO();
            d.setRunId(runId);
            d.setMaterialId(f.getKey());
            d.setFingerprint(f.getValue());
            fingerprintMapper.insert(d);
        }
        fingerprintMapper.deleteOtherRuns(runId);
        PmcMrpRunDO r = runMapper.selectById(runId);
        r.setRunStatus(SUCCESS);
        r.setFinishedAt(LocalDateTime.now());
        r.setProgress(100);
        r.setMaterialCount(partial ? changed.size() : out.materialCount());
        r.setSuggestionCount(Math.toIntExact(resultMapper.selectCount(new LambdaQueryWrapper<PmcMrpResultDO>().eq(PmcMrpResultDO::getRunId, runId))));
        r.setExceptionCount(Math.toIntExact(exceptionMapper.selectCount(new LambdaQueryWrapper<PmcMrpExceptionDO>().eq(PmcMrpExceptionDO::getRunId, runId))));
        if (nc != null) {
            Map<String, Object> params = new LinkedHashMap<>(paramsOf(r.getParams()));
            Map<String, Object> info = new LinkedHashMap<>();
            if (partial) {
                info.put("baseRunNo", nc.base().getRunNo());
                info.put("changedMaterials", changed.size());
                info.put("totalMaterials", out.materialCount());
            } else {
                info.put("fallback", nc.reason());
            }
            params.put("netChange", info);
            r.setParams(json(params));
        }
        runMapper.updateByIdOrFail(r);
        eventPublisher.publish(new MrpRunCompletedEvent(r.getId(), r.getRunNo(), SUCCESS, r.getMaterialCount(), r.getSuggestionCount(), r.getOperatorId()));
        if (r.getOperatorId() != null) {
            support.message(List.of(r.getOperatorId()), "MRP 运算完成", r.getRunNo() + "：物料 " + r.getMaterialCount() + " 个，建议 "
                    + r.getSuggestionCount() + " 条，例外 " + r.getExceptionCount() + " 条", "/pmc/mrp/suggestions?runId=" + r.getId());
        }
    }

    // ==================== 查询 ====================

    public PmcMrpRunDO getOrThrow(Long id) {
        PmcMrpRunDO r = id == null ? null : runMapper.selectById(id);
        if (r == null) throw new BizException(PmcErrorCodes.MRP_RUN_NOT_EXISTS);
        return r;
    }

    /** 最近一次成功运算 */
    public PmcMrpRunDO latestSuccess() {
        return runMapper.selectOne(new LambdaQueryWrapper<PmcMrpRunDO>().eq(PmcMrpRunDO::getRunStatus, SUCCESS)
                .orderByDesc(PmcMrpRunDO::getStartedAt).orderByDesc(PmcMrpRunDO::getId).last("LIMIT 1"));
    }

    public PageResult<RunRow> page(RunQuery q) {
        LambdaQueryWrapper<PmcMrpRunDO> w = new LambdaQueryWrapper<PmcMrpRunDO>()
                .eq(StringUtils.hasText(q.getRunStatus()), PmcMrpRunDO::getRunStatus, q.getRunStatus())
                .ge(q.getDateFrom() != null, PmcMrpRunDO::getStartedAt, q.getDateFrom() == null ? null : q.getDateFrom().atStartOfDay())
                .lt(q.getDateTo() != null, PmcMrpRunDO::getStartedAt, q.getDateTo() == null ? null : q.getDateTo().plusDays(1).atStartOfDay())
                .orderByDesc(PmcMrpRunDO::getStartedAt).orderByDesc(PmcMrpRunDO::getId);
        IPage<PmcMrpRunDO> p = runMapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public RunRow get(Long id) {
        return rows(List.of(getOrThrow(id))).get(0);
    }

    private List<RunRow> rows(List<PmcMrpRunDO> list) {
        PmcMrpRunDO latest = latestSuccess();
        Map<Long, UserDTO> users = support.users(list.stream().map(PmcMrpRunDO::getOperatorId).toList());
        return list.stream().map(r -> new RunRow(r.getId(), r.getRunNo(), r.getRunType(), r.getRunStatus(), r.getProgress(), r.getStartedAt(),
                r.getFinishedAt(), r.getFinishedAt() == null ? null : Duration.between(r.getStartedAt(), r.getFinishedAt()).toSeconds(),
                r.getMaterialCount(), r.getSuggestionCount(), r.getExceptionCount(), r.getOperatorId(), PmcSupport.name(users, r.getOperatorId()),
                r.getErrorMsg(), r.getParams(), latest != null && latest.getId().equals(r.getId()))).toList();
    }

    /** 运算中断检查：RUNNING 超过 1 小时置为失败（PMC-MRP-R04） */
    @ErpJob(code = "PMC_MRP_STALE_CHECK", name = "MRP 中断运算检查", cron = "0 */10 * * * ?")
    public String staleCheck() {
        int n = 0;
        for (PmcMrpRunDO r : runMapper.selectList(new LambdaQueryWrapper<PmcMrpRunDO>().eq(PmcMrpRunDO::getRunStatus, RUNNING)
                .lt(PmcMrpRunDO::getStartedAt, LocalDateTime.now().minusHours(1)))) {
            fail(r.getId(), "运算中断");
            n++;
        }
        return "处理中断运算 " + n + " 个";
    }
}
