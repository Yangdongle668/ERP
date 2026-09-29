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
import com.erp.module.pmc.dal.dataobject.PmcMrpPeggingDO;
import com.erp.module.pmc.dal.dataobject.PmcMrpResultDO;
import com.erp.module.pmc.dal.dataobject.PmcMrpRunDO;
import com.erp.module.pmc.dal.mapper.PmcMrpBalanceMapper;
import com.erp.module.pmc.dal.mapper.PmcMrpExceptionMapper;
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
 */
@Service("pmcMrpRunService")
public class MrpRunService {

    private static final Logger LOG = LoggerFactory.getLogger(MrpRunService.class);
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    private static final Map<String, Integer> BALANCE_ORDER = Map.of("OPENING", 0, "QC", 1, "PURCHASE", 1, "WIP", 1, "PLANNED", 2);

    private final PmcMrpRunMapper runMapper;
    private final PmcMrpResultMapper resultMapper;
    private final PmcMrpPeggingMapper peggingMapper;
    private final PmcMrpExceptionMapper exceptionMapper;
    private final PmcMrpBalanceMapper balanceMapper;
    private final MrpInputLoader loader;
    private final PmcSupport support;
    private final PlanningData data;
    private final AsyncTaskApi asyncTaskApi;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    public MrpRunService(PmcMrpRunMapper runMapper, PmcMrpResultMapper resultMapper, PmcMrpPeggingMapper peggingMapper,
                         PmcMrpExceptionMapper exceptionMapper, PmcMrpBalanceMapper balanceMapper, MrpInputLoader loader, PmcSupport support,
                         PlanningData data, AsyncTaskApi asyncTaskApi, DomainEventPublisher eventPublisher, TransactionTemplate tx,
                         ObjectMapper objectMapper) {
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.peggingMapper = peggingMapper;
        this.exceptionMapper = exceptionMapper;
        this.balanceMapper = balanceMapper;
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
        return new MrpInputLoader.Options(type, lines, horizon, forecast, safety, p.getBool(PmcModuleConfig.P_USE_MPS), p.getInt(PmcModuleConfig.P_TOLERANCE));
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
            tx.executeWithoutResult(s -> persist(runId, in, out));
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

    private void persist(Long runId, MrpModel.Input in, Output out) {
        Map<Long, Mat> mats = in.mats();
        Map<Long, Long> suppliers = new HashMap<>();
        for (Planned p : out.planned()) {
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
            for (Peg g : p.pegs) {
                PmcMrpPeggingDO d = new PmcMrpPeggingDO();
                d.setRunId(runId);
                d.setResultId(p.resultId);
                d.setDemandType(g.demandType());
                d.setSourceId(g.sourceId());
                d.setSourceNo(g.sourceNo());
                d.setParentMaterialId(g.parentMaterialId());
                d.setParentResultId(g.parent() == null ? null : g.parent().resultId);
                d.setQty(g.qty());
                d.setRequiredDate(g.date());
                peggingMapper.insert(d);
            }
        }
        for (MrpModel.Exception e : out.exceptions()) {
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
        for (Balance b : out.balances()) byMat.computeIfAbsent(b.materialId(), k -> new ArrayList<>()).add(b);
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
        PmcMrpRunDO r = runMapper.selectById(runId);
        r.setRunStatus(SUCCESS);
        r.setFinishedAt(LocalDateTime.now());
        r.setProgress(100);
        r.setMaterialCount(out.materialCount());
        r.setSuggestionCount(out.planned().size());
        r.setExceptionCount(out.exceptions().size());
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
