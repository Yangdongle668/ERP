package com.erp.module.pmc.service.mrp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPlanAttr;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.pmc.api.PmcErrorCodes;
import com.erp.module.pmc.controller.vo.CommonVOs.BatchResult;
import com.erp.module.pmc.controller.vo.MrpVOs.Balance;
import com.erp.module.pmc.controller.vo.MrpVOs.BalanceRow;
import com.erp.module.pmc.controller.vo.MrpVOs.ExceptionQuery;
import com.erp.module.pmc.controller.vo.MrpVOs.ExceptionRow;
import com.erp.module.pmc.controller.vo.MrpVOs.PegRow;
import com.erp.module.pmc.controller.vo.MrpVOs.SuggestionQuery;
import com.erp.module.pmc.controller.vo.MrpVOs.SuggestionRow;
import com.erp.module.pmc.controller.vo.MrpVOs.SuggestionUpdate;
import com.erp.module.pmc.dal.dataobject.PmcMrpBalanceDO;
import com.erp.module.pmc.dal.dataobject.PmcMrpExceptionDO;
import com.erp.module.pmc.dal.dataobject.PmcMrpPeggingDO;
import com.erp.module.pmc.dal.dataobject.PmcMrpResultDO;
import com.erp.module.pmc.dal.dataobject.PmcMrpRunDO;
import com.erp.module.pmc.dal.mapper.PmcMrpBalanceMapper;
import com.erp.module.pmc.dal.mapper.PmcMrpExceptionMapper;
import com.erp.module.pmc.dal.mapper.PmcMrpPeggingMapper;
import com.erp.module.pmc.dal.mapper.PmcMrpResultMapper;
import com.erp.module.pmc.service.PlanningData;
import com.erp.module.pmc.service.PmcSupport;
import com.erp.module.production.api.order.MrpSuggestion;
import com.erp.module.production.api.order.ProductionOrderApi;
import com.erp.module.purchase.api.outsourcing.MrpOutsourceSuggestion;
import com.erp.module.purchase.api.outsourcing.OutsourcingApi;
import com.erp.module.purchase.api.order.InTransitDTO;
import com.erp.module.purchase.api.requisition.MrpPurchaseSuggestion;
import com.erp.module.purchase.api.requisition.PurchaseRequisitionApi;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** MRP 建议处理（需求 06-04）：调整、转单、忽略、需求追溯；例外推送；供需平衡 */
@Service("pmcSuggestionService")
public class SuggestionService {

    public static final String PENDING = "PENDING";
    public static final String CONVERTED = "CONVERTED";
    public static final String IGNORED = "IGNORED";
    public static final String SUPERSEDED = "SUPERSEDED";
    private static final Map<String, String> STATUS_LABEL = Map.of(CONVERTED, "已转单", IGNORED, "已忽略", SUPERSEDED, "已过期", PENDING, "待处理");

    private final PmcMrpResultMapper resultMapper;
    private final PmcMrpPeggingMapper peggingMapper;
    private final PmcMrpExceptionMapper exceptionMapper;
    private final PmcMrpBalanceMapper balanceMapper;
    private final MrpRunService runService;
    private final PmcSupport support;
    private final PlanningData data;
    private final PurchaseRequisitionApi requisitionApi;
    private final OutsourcingApi outsourcingApi;
    private final ProductionOrderApi productionOrderApi;
    private final TransactionTemplate tx;

    public SuggestionService(PmcMrpResultMapper resultMapper, PmcMrpPeggingMapper peggingMapper, PmcMrpExceptionMapper exceptionMapper,
                             PmcMrpBalanceMapper balanceMapper, MrpRunService runService, PmcSupport support, PlanningData data,
                             PurchaseRequisitionApi requisitionApi, OutsourcingApi outsourcingApi, ProductionOrderApi productionOrderApi,
                             TransactionTemplate tx) {
        this.resultMapper = resultMapper;
        this.peggingMapper = peggingMapper;
        this.exceptionMapper = exceptionMapper;
        this.balanceMapper = balanceMapper;
        this.runService = runService;
        this.support = support;
        this.data = data;
        this.requisitionApi = requisitionApi;
        this.outsourcingApi = outsourcingApi;
        this.productionOrderApi = productionOrderApi;
        this.tx = tx;
    }

    private Long runIdOf(Long runId) {
        if (runId != null) return runId;
        PmcMrpRunDO r = runService.latestSuccess();
        return r == null ? -1L : r.getId();
    }

    // ==================== 建议列表 ====================

    public PageResult<SuggestionRow> page(SuggestionQuery q) {
        Long runId = runIdOf(q.getRunId());
        List<String> statuses = StringUtils.hasText(q.getStatuses()) ? Arrays.asList(q.getStatuses().split(",")) : List.of(PENDING);
        LambdaQueryWrapper<PmcMrpResultDO> w = new LambdaQueryWrapper<PmcMrpResultDO>().eq(PmcMrpResultDO::getRunId, runId)
                .eq(StringUtils.hasText(q.getType()), PmcMrpResultDO::getSuggestionType, q.getType())
                .eq(q.getMaterialId() != null, PmcMrpResultDO::getMaterialId, q.getMaterialId())
                .eq(q.getPlannerId() != null, PmcMrpResultDO::getPlannerId, q.getPlannerId())
                .eq(q.getBuyerId() != null, PmcMrpResultDO::getBuyerId, q.getBuyerId())
                .eq(q.getSupplierId() != null, PmcMrpResultDO::getSupplierId, q.getSupplierId())
                .ge(q.getReleaseFrom() != null, PmcMrpResultDO::getReleaseDate, q.getReleaseFrom())
                .le(q.getReleaseTo() != null, PmcMrpResultDO::getReleaseDate, q.getReleaseTo())
                .eq(Boolean.TRUE.equals(q.getLateOnly()), PmcMrpResultDO::getIsLate, true)
                .in(!statuses.contains("ALL"), PmcMrpResultDO::getSuggestionStatus, statuses);
        if (q.getCategoryId() != null) {
            Set<Long> mids = resultMapper.selectList(w.clone().select(PmcMrpResultDO::getMaterialId)).stream().map(PmcMrpResultDO::getMaterialId)
                    .collect(Collectors.toSet());
            List<Long> in = support.materials(mids).values().stream().filter(m -> q.getCategoryId().equals(m.categoryId())).map(MaterialDTO::id).toList();
            if (in.isEmpty()) return new PageResult<>(List.of(), 0L);
            w.in(PmcMrpResultDO::getMaterialId, in);
        }
        w.orderByAsc(PmcMrpResultDO::getReleaseDate).orderByAsc(PmcMrpResultDO::getId);
        IPage<PmcMrpResultDO> p = resultMapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    private List<SuggestionRow> rows(List<PmcMrpResultDO> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> mids = list.stream().map(PmcMrpResultDO::getMaterialId).collect(Collectors.toSet());
        Map<Long, MaterialDTO> ms = support.materials(mids);
        Map<Long, BigDecimal> avail = data.available(mids);
        Map<Long, InTransitDTO> transit = data.inTransit(mids);
        Map<Long, MaterialPlanAttr> attrs = support.planAttrs(mids);
        Map<Long, SupplierDTO> sups = data.suppliers(list.stream().map(PmcMrpResultDO::getSupplierId).toList());
        Set<Long> uids = new HashSet<>();
        list.forEach(r -> {
            uids.add(r.getPlannerId());
            uids.add(r.getBuyerId());
        });
        Map<Long, UserDTO> users = support.users(uids);
        Map<Long, List<PmcMrpPeggingDO>> pegs = peggingMapper.selectList(new LambdaQueryWrapper<PmcMrpPeggingDO>()
                        .in(PmcMrpPeggingDO::getResultId, list.stream().map(PmcMrpResultDO::getId).toList()))
                .stream().collect(Collectors.groupingBy(PmcMrpPeggingDO::getResultId));
        LocalDate today = LocalDate.now();
        List<SuggestionRow> out = new ArrayList<>();
        for (PmcMrpResultDO r : list) {
            MaterialDTO m = ms.get(r.getMaterialId());
            MaterialPlanAttr a = attrs.get(r.getMaterialId());
            SupplierDTO s = sups.get(r.getSupplierId());
            InTransitDTO t = transit.get(r.getMaterialId());
            int lateDays = 0;
            if (Boolean.TRUE.equals(r.getIsLate())) {
                int lead = a == null ? 0 : a.leadTimeDays();
                lateDays = (int) Math.max(0, ChronoUnit.DAYS.between(r.getRequiredDate().minusDays(lead), today));
            }
            out.add(new SuggestionRow(r.getId(), r.getRunId(), r.getSuggestionType(), r.getMaterialId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(), r.getQty(), r.getOriginalQty(),
                    r.getNetRequirement(), r.getRequiredDate(), r.getReleaseDate(), Boolean.TRUE.equals(r.getIsLate()), lateDays, r.getSupplierId(),
                    s == null ? null : s.name(), r.getPlannerId(), PmcSupport.name(users, r.getPlannerId()), r.getBuyerId(),
                    PmcSupport.name(users, r.getBuyerId()), r.getBomId(), r.getDeptId(), avail.getOrDefault(r.getMaterialId(), BigDecimal.ZERO),
                    t == null ? BigDecimal.ZERO : t.qty(), summary(pegs.getOrDefault(r.getId(), List.of())), r.getSuggestionStatus(),
                    r.getConvertedDocType(), r.getConvertedDocId(), r.getConvertedDocNo(), r.getIgnoreReason(), a == null ? null : a.moq(),
                    a == null ? null : a.mpq(), r.getVersion() == null ? 0 : r.getVersion()));
        }
        return out;
    }

    private static String summary(List<PmcMrpPeggingDO> pegs) {
        if (pegs.isEmpty()) return null;
        List<String> nos = pegs.stream().map(p -> p.getSourceNo() == null ? label(p.getDemandType()) : p.getSourceNo()).distinct().toList();
        return nos.size() <= 2 ? String.join("、", nos) : String.join("、", nos.subList(0, 2)) + " 等 " + nos.size() + " 项";
    }

    private static String label(String type) {
        return switch (type) {
            case "SAFETY_STOCK" -> "安全库存";
            case "FORECAST" -> "预测";
            case "MANUAL" -> "手工需求";
            case "ALLOCATION" -> "在制分配";
            default -> type;
        };
    }

    private PmcMrpResultDO requirePending(Long id) {
        PmcMrpResultDO r = id == null ? null : resultMapper.selectById(id);
        if (r == null) throw new BizException(PmcErrorCodes.SUGGESTION_NOT_EXISTS);
        if (SUPERSEDED.equals(r.getSuggestionStatus())) throw new BizException(PmcErrorCodes.SUGGESTION_SUPERSEDED);
        PmcMrpRunDO latest = runService.latestSuccess();
        if (latest == null || !latest.getId().equals(r.getRunId())) throw new BizException(PmcErrorCodes.SUGGESTION_SUPERSEDED);
        if (!PENDING.equals(r.getSuggestionStatus())) {
            throw BizException.of(PmcErrorCodes.SUGGESTION_NOT_PENDING, STATUS_LABEL.getOrDefault(r.getSuggestionStatus(), r.getSuggestionStatus()));
        }
        return r;
    }

    /** 调整数量、日期、供应商、BOM；返回 MOQ / MPQ 提示（PMC-SUG-R03，警告不阻止） */
    @Transactional(rollbackFor = Exception.class)
    public List<String> update(Long id, SuggestionUpdate req) {
        PmcMrpResultDO r = requirePending(id);
        List<String> warnings = new ArrayList<>();
        if (req.qty() != null) {
            if (req.qty().signum() <= 0) throw new BizException(PmcErrorCodes.SUGGESTION_QTY_INVALID);
            MaterialPlanAttr a = support.materialApi().getPlanAttr(r.getMaterialId());
            if (a != null && a.moq() != null && a.moq().signum() > 0 && req.qty().compareTo(a.moq()) < 0) {
                warnings.add("数量低于最小订购量 " + PmcSupport.plain(a.moq()));
            }
            if (a != null && a.mpq() != null && a.mpq().signum() > 0 && req.qty().remainder(a.mpq()).signum() != 0) {
                warnings.add("数量不是最小包装量 " + PmcSupport.plain(a.mpq()) + " 的倍数");
            }
            r.setQty(req.qty());
        }
        if (req.requiredDate() != null && !req.requiredDate().equals(r.getRequiredDate())) {
            MaterialPlanAttr a = support.materialApi().getPlanAttr(r.getMaterialId());
            int lead = a == null ? 0 : a.leadTimeDays();
            LocalDate release = req.requiredDate().minusDays(lead);
            r.setRequiredDate(req.requiredDate());
            r.setIsLate(release.isBefore(LocalDate.now()));
            r.setReleaseDate(release.isBefore(LocalDate.now()) ? LocalDate.now() : release);
        }
        if (req.supplierId() != null) r.setSupplierId(req.supplierId());
        if (req.bomId() != null) r.setBomId(req.bomId());
        if (req.deptId() != null) r.setDeptId(req.deptId());
        resultMapper.updateByIdOrFail(r);
        return warnings;
    }

    /** 忽略（原因必填） */
    @Transactional(rollbackFor = Exception.class)
    public int ignore(List<Long> ids, String reason) {
        String why = PmcSupport.requireReason(reason, "忽略");
        int n = 0;
        for (Long id : ids == null ? List.<Long>of() : ids) {
            PmcMrpResultDO r = requirePending(id);
            r.setSuggestionStatus(IGNORED);
            r.setIgnoreReason(why);
            r.setHandledBy(support.currentUser());
            r.setHandledAt(LocalDateTime.now());
            resultMapper.updateByIdOrFail(r);
            n++;
        }
        return n;
    }

    /**
     * 转单（PMC-SUG-R01、R02、R04）：采购 → 采购申请（按计划员合并）；生产 → 已计划的生产订单（可同时下达）；委外 → 委外单草稿。
     * 逐条校验，失败的列出原因，成功的照常转换。
     */
    public BatchResult convert(List<Long> ids, boolean release) {
        List<String> errors = new ArrayList<>();
        List<String> docNos = new ArrayList<>();
        List<PmcMrpResultDO> ok = new ArrayList<>();
        String type = null;
        Map<Long, MaterialDTO> ms = new HashMap<>();
        List<PmcMrpResultDO> all = ids == null || ids.isEmpty() ? List.of() : resultMapper.selectBatchIds(ids);
        ms.putAll(support.materials(all.stream().map(PmcMrpResultDO::getMaterialId).toList()));
        for (Long id : ids == null ? List.<Long>of() : ids) {
            PmcMrpResultDO r = all.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
            String code = r == null ? String.valueOf(id) : PmcSupport.code(ms, r.getMaterialId());
            try {
                PmcMrpResultDO p = requirePending(id);
                if (type == null) type = p.getSuggestionType();
                else if (!type.equals(p.getSuggestionType())) throw new BizException(PmcErrorCodes.SUGGESTION_TYPE_MISMATCH);
                MaterialDTO m = ms.get(p.getMaterialId());
                if (m == null || m.status() == MaterialStatus.DISABLED) throw BizException.of(PmcErrorCodes.SUGGESTION_MATERIAL_DISABLED, code);
                if (!"MAKE".equals(p.getSuggestionType()) && p.getSupplierId() != null) data.supplierApi().validateQualified(p.getSupplierId());
                ok.add(p);
            } catch (BizException ex) {
                errors.add(code + "：" + ex.getMessage());
            }
        }
        if (ok.isEmpty()) return new BatchResult(0, errors, docNos);
        int success = 0;
        switch (type) {
            case "PURCHASE" -> {
                List<MrpPurchaseSuggestion> reqs = ok.stream().map(r -> new MrpPurchaseSuggestion(r.getRunId(), r.getId(), r.getMaterialId(), r.getQty(),
                        r.getRequiredDate(), r.getPlannerId(), r.getSupplierId(), source(r.getId()))).toList();
                try {
                    tx.executeWithoutResult(s -> {
                        List<Long> out = requisitionApi.createFromMrp(reqs);
                        for (PmcMrpResultDO r : ok) markConverted(r, "PUR_REQUISITION", out.size() == 1 ? out.get(0) : null, null);
                    });
                    success = ok.size();
                } catch (BizException ex) {
                    errors.add(ex.getMessage());
                }
            }
            case "MAKE" -> {
                for (PmcMrpResultDO r : ok) {
                    try {
                        tx.executeWithoutResult(s -> {
                            MaterialPlanAttr a = support.materialApi().getPlanAttr(r.getMaterialId());
                            int lead = a == null ? 0 : a.leadTimeDays();
                            LocalDate start = r.getRequiredDate().minusDays(lead);
                            if (start.isBefore(LocalDate.now())) start = LocalDate.now();
                            LocalDate end = r.getRequiredDate().isBefore(start) ? start : r.getRequiredDate();
                            Long orderId = productionOrderApi.createFromMrp(List.of(new MrpSuggestion(r.getId(), "MRP#" + r.getId(), r.getMaterialId(),
                                    r.getQty(), start, end, r.getPlannerId(), r.getDeptId(), null, source(r.getId())))).get(0);
                            if (release) productionOrderApi.release(orderId);
                            markConverted(r, "MFG_PROD_ORDER", orderId, null);
                        });
                        success++;
                    } catch (BizException ex) {
                        errors.add(PmcSupport.code(ms, r.getMaterialId()) + "：" + ex.getMessage());
                    }
                }
            }
            default -> {
                for (PmcMrpResultDO r : ok) {
                    try {
                        tx.executeWithoutResult(s -> {
                            Long docId = outsourcingApi.createFromMrp(List.of(new MrpOutsourceSuggestion(r.getId(), r.getMaterialId(), r.getQty(),
                                    r.getRequiredDate(), r.getSupplierId()))).get(0);
                            markConverted(r, "PUR_OUTSOURCING", docId, null);
                        });
                        success++;
                    } catch (BizException ex) {
                        errors.add(PmcSupport.code(ms, r.getMaterialId()) + "：" + ex.getMessage());
                    }
                }
            }
        }
        return new BatchResult(success, errors, docNos);
    }

    private void markConverted(PmcMrpResultDO r, String docType, Long docId, String docNo) {
        PmcMrpResultDO fresh = resultMapper.selectById(r.getId());
        fresh.setSuggestionStatus(CONVERTED);
        fresh.setConvertedDocType(docType);
        fresh.setConvertedDocId(docId);
        fresh.setConvertedDocNo(docNo);
        fresh.setHandledBy(support.currentUser());
        fresh.setHandledAt(LocalDateTime.now());
        resultMapper.updateByIdOrFail(fresh);
    }

    /** 需求来源说明（采购申请行、生产订单备注） */
    private String source(Long resultId) {
        List<PmcMrpPeggingDO> pegs = topPegs(resultId, 0);
        String s = pegs.stream().map(p -> p.getSourceNo() == null ? label(p.getDemandType()) : p.getSourceNo()).distinct()
                .collect(Collectors.joining("、"));
        return s.length() > 200 ? s.substring(0, 200) : s.isEmpty() ? null : s;
    }

    /** 追溯到顶层需求（上层为计划订单时继续向上） */
    private List<PmcMrpPeggingDO> topPegs(Long resultId, int depth) {
        List<PmcMrpPeggingDO> out = new ArrayList<>();
        for (PmcMrpPeggingDO p : peggingMapper.selectList(new LambdaQueryWrapper<PmcMrpPeggingDO>().eq(PmcMrpPeggingDO::getResultId, resultId))) {
            if (p.getParentResultId() != null && depth < 20) out.addAll(topPegs(p.getParentResultId(), depth + 1));
            else out.add(p);
        }
        return out;
    }

    public List<PegRow> pegging(Long resultId) {
        List<PmcMrpPeggingDO> list = peggingMapper.selectList(new LambdaQueryWrapper<PmcMrpPeggingDO>().eq(PmcMrpPeggingDO::getResultId, resultId)
                .orderByAsc(PmcMrpPeggingDO::getRequiredDate).orderByAsc(PmcMrpPeggingDO::getId));
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(PmcMrpPeggingDO::getParentMaterialId).toList());
        return list.stream().map(p -> {
            MaterialDTO m = ms.get(p.getParentMaterialId());
            return new PegRow(p.getId(), p.getDemandType(), p.getSourceId(), p.getSourceNo() == null ? label(p.getDemandType()) : p.getSourceNo(),
                    p.getParentMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), p.getParentResultId(), p.getQty(),
                    p.getRequiredDate());
        }).toList();
    }

    // ==================== 例外 ====================

    public PageResult<ExceptionRow> exceptions(ExceptionQuery q) {
        Long runId = runIdOf(q.getRunId());
        LambdaQueryWrapper<PmcMrpExceptionDO> w = new LambdaQueryWrapper<PmcMrpExceptionDO>().eq(PmcMrpExceptionDO::getRunId, runId)
                .eq(StringUtils.hasText(q.getType()), PmcMrpExceptionDO::getExceptionType, q.getType())
                .eq(q.getMaterialId() != null, PmcMrpExceptionDO::getMaterialId, q.getMaterialId())
                .eq(q.getHandled() != null, PmcMrpExceptionDO::getHandled, q.getHandled())
                .eq(q.getOwnerId() != null, PmcMrpExceptionDO::getOwnerId, q.getOwnerId())
                .orderByAsc(PmcMrpExceptionDO::getExceptionType).orderByAsc(PmcMrpExceptionDO::getId);
        IPage<PmcMrpExceptionDO> p = exceptionMapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        List<PmcMrpExceptionDO> list = p.getRecords();
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(PmcMrpExceptionDO::getMaterialId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(PmcMrpExceptionDO::getOwnerId).toList());
        return new PageResult<>(list.stream().map(e -> {
            MaterialDTO m = ms.get(e.getMaterialId());
            return new ExceptionRow(e.getId(), e.getRunId(), e.getExceptionType(), e.getMaterialId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), e.getDocType(), e.getDocId(), e.getDocNo(), e.getSupplyDate(), e.getSuggestedDate(), e.getQty(),
                    e.getMessage(), e.getOwnerId(), PmcSupport.name(users, e.getOwnerId()), Boolean.TRUE.equals(e.getHandled()), e.getPushedAt());
        }).toList(), p.getTotal());
    }

    /** 推送处理：给负责人（采购员 / 计划员）发送工作台待办；没有负责人的计入失败 */
    @Transactional(rollbackFor = Exception.class)
    public BatchResult push(List<Long> ids) {
        int n = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            PmcMrpExceptionDO e = exceptionMapper.selectById(id);
            if (e == null) throw new BizException(PmcErrorCodes.EXCEPTION_NOT_EXISTS);
            if (e.getOwnerId() == null) {
                errors.add(e.getMessage() + "：物料没有采购员 / 计划员");
                continue;
            }
            support.todo("PMC_MRP_EXCEPTION:" + e.getId(), List.of(e.getOwnerId()), "PMC_MRP_EXCEPTION", e.getId(), e.getDocNo(),
                    "MRP 例外：" + e.getMessage(), "/pmc/mrp/suggestions?tab=exception&runId=" + e.getRunId());
            e.setPushedAt(LocalDateTime.now());
            exceptionMapper.updateByIdOrFail(e);
            n++;
        }
        return new BatchResult(n, errors, List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public void handled(Long id, boolean handled) {
        PmcMrpExceptionDO e = exceptionMapper.selectById(id);
        if (e == null) throw new BizException(PmcErrorCodes.EXCEPTION_NOT_EXISTS);
        e.setHandled(handled);
        exceptionMapper.updateByIdOrFail(e);
    }

    // ==================== 供需平衡 ====================

    public Balance balance(Long materialId, Long runId) {
        PmcMrpRunDO run = runId != null ? runService.getOrThrow(runId) : runService.latestSuccess();
        MaterialDTO m = support.material(materialId);
        if (run == null) return new Balance(null, null, materialId, m.code(), m.name(), m.baseUom(), BigDecimal.ZERO, List.of());
        List<PmcMrpBalanceDO> list = balanceMapper.selectList(new LambdaQueryWrapper<PmcMrpBalanceDO>().eq(PmcMrpBalanceDO::getRunId, run.getId())
                .eq(PmcMrpBalanceDO::getMaterialId, materialId).orderByAsc(PmcMrpBalanceDO::getSeq));
        Map<Long, MaterialDTO> parents = support.materials(list.stream().map(PmcMrpBalanceDO::getParentMaterialId).toList());
        BigDecimal ss = list.isEmpty() ? BigDecimal.ZERO : list.get(0).getSafetyStock();
        List<BalanceRow> rows = new ArrayList<>();
        for (PmcMrpBalanceDO b : list) {
            rows.add(new BalanceRow(b.getBalDate(), b.getEntryType(), b.getDocNo(), b.getParentMaterialId(), PmcSupport.code(parents, b.getParentMaterialId()),
                    b.getDemandQty(), b.getSupplyQty(), b.getProjectedQty(), b.getSafetyStock()));
        }
        return new Balance(run.getId(), run.getRunNo(), materialId, m.code(), m.name(), m.baseUom(), ss, rows);
    }
}
