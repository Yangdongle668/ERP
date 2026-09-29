package com.erp.module.quality.service.inspection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.util.Decimals;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialQualityAttr;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.JudgeResult;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.TransferRequest;
import com.erp.module.inventory.api.doc.TransferType;
import com.erp.module.production.api.finish.ProductionFinishApi;
import com.erp.module.purchase.api.receipt.PurchaseReceiptApi;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.api.inspection.InspectionCreatedEvent;
import com.erp.module.quality.api.inspection.InspectionJudgedEvent;
import com.erp.module.quality.api.inspection.InspectionRejudgedEvent;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.quality.controller.vo.BasicVOs.LevelPlan;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingResult;
import com.erp.module.quality.controller.vo.InspectionVOs.BatchJudgeResult;
import com.erp.module.quality.controller.vo.InspectionVOs.DefectSave;
import com.erp.module.quality.controller.vo.InspectionVOs.ItemSave;
import com.erp.module.quality.controller.vo.InspectionVOs.JudgeReq;
import com.erp.module.quality.controller.vo.InspectionVOs.ResultsSave;
import com.erp.module.quality.dal.dataobject.QcDefectCodeDO;
import com.erp.module.quality.dal.dataobject.QcInspectionDO;
import com.erp.module.quality.dal.dataobject.QcInspectionDefectDO;
import com.erp.module.quality.dal.dataobject.QcInspectionItemDO;
import com.erp.module.quality.dal.dataobject.QcSamplingPlanDO;
import com.erp.module.quality.dal.dataobject.QcStandardDO;
import com.erp.module.quality.dal.dataobject.QcStandardItemDO;
import com.erp.module.quality.dal.mapper.QcInspectionDefectMapper;
import com.erp.module.quality.dal.mapper.QcInspectionItemMapper;
import com.erp.module.quality.dal.mapper.QcInspectionMapper;
import com.erp.module.quality.service.InspStatus;
import com.erp.module.quality.service.QcAction;
import com.erp.module.quality.service.QcStateMachines;
import com.erp.module.quality.service.QcSupport;
import com.erp.module.quality.service.basic.BasicDataService;
import com.erp.module.quality.service.basic.SamplingService;
import com.erp.module.quality.service.basic.StandardService;
import com.erp.module.quality.service.ncr.NcrService;
import com.erp.module.sales.api.returns.SalesReturnApi;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 检验单：生成（事件 / 手工）、结果录入、判定、提交 MRB、重判、检验调拨完成（10-02）。
 * 判定后在同一事务内：生成检验调拨、回写上游（到货行 / 完工入库 / 销售退货行）、发布 {@link InspectionJudgedEvent}。
 */
@Service
public class InspectionService {

    /** 检验调拨的来源类型（仓库 cancelBySource 按它作废未确认的调拨单） */
    public static final String TRANSFER_SOURCE = "QC_INSPECTION";
    public static final String SRC_STOCK_IN = "STOCK_IN";
    public static final String SRC_REPORT = "MFG_REPORT";
    public static final String SRC_NOTICE = "SHP_NOTICE";
    public static final String SRC_TRANSFER = "INV_TRANSFER";
    public static final String SRC_MANUAL = "MANUAL";

    public static final String QUALIFIED = "QUALIFIED";
    public static final String REJECTED = "REJECTED";
    public static final String CONCESSION = "CONCESSION";
    public static final String SORTED = "SORTED";
    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";

    private final QcInspectionMapper mapper;
    private final QcInspectionItemMapper itemMapper;
    private final QcInspectionDefectMapper defectMapper;
    private final StandardService standardService;
    private final SamplingService samplingService;
    private final BasicDataService basicDataService;
    private final InventoryDocApi inventoryDocApi;
    private final PurchaseReceiptApi purchaseReceiptApi;
    private final ProductionFinishApi productionFinishApi;
    private final SalesReturnApi salesReturnApi;
    private final WorkflowApi workflowApi;
    private final DomainEventPublisher eventPublisher;
    private final ObjectProvider<NcrService> ncrService;
    private final QcSupport support;
    private final TransactionTemplate tx;

    public InspectionService(QcInspectionMapper mapper, QcInspectionItemMapper itemMapper, QcInspectionDefectMapper defectMapper,
                             StandardService standardService, SamplingService samplingService, BasicDataService basicDataService,
                             InventoryDocApi inventoryDocApi, PurchaseReceiptApi purchaseReceiptApi, ProductionFinishApi productionFinishApi,
                             SalesReturnApi salesReturnApi, WorkflowApi workflowApi, DomainEventPublisher eventPublisher,
                             ObjectProvider<NcrService> ncrService, QcSupport support, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.itemMapper = itemMapper;
        this.defectMapper = defectMapper;
        this.standardService = standardService;
        this.samplingService = samplingService;
        this.basicDataService = basicDataService;
        this.inventoryDocApi = inventoryDocApi;
        this.purchaseReceiptApi = purchaseReceiptApi;
        this.productionFinishApi = productionFinishApi;
        this.salesReturnApi = salesReturnApi;
        this.workflowApi = workflowApi;
        this.eventPublisher = eventPublisher;
        this.ncrService = ncrService;
        this.support = support;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ==================== 生成 ====================

    /** 生成检验单的参数（数量为基本单位） */
    public record CreateCmd(InspectType type, String ipqcKind, Long materialId, String batchNo, BigDecimal lotQty, Long supplierId, Long customerId,
                            String sourceType, Long sourceId, Long sourceLineId, String sourceNo, String upstreamType, Long upstreamId,
                            Long upstreamLineId, String upstreamNo, Long prodOrderId, Integer operationSeq, String operation, Long warehouseId,
                            String remark) {
    }

    /**
     * QC-INS-R01、R02：同一来源行不重复生成；按标准匹配计算样本量并保存快照；物料免检（或方案为免检）时直接判定合格。
     *
     * @return 检验单 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(CreateCmd c) {
        if (c.lotQty() == null || c.lotQty().signum() <= 0) throw new BizException(QualityErrorCodes.LOT_QTY_INVALID);
        if (c.sourceType() != null && c.sourceId() != null) {
            QcInspectionDO exist = mapper.selectOne(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getSourceType, c.sourceType())
                    .eq(QcInspectionDO::getSourceId, c.sourceId())
                    .eq(c.sourceLineId() != null, QcInspectionDO::getSourceLineId, c.sourceLineId())
                    .eq(QcInspectionDO::getMaterialId, c.materialId())
                    .eq(StringUtils.hasText(c.batchNo()), QcInspectionDO::getBatchNo, c.batchNo())
                    .ne(QcInspectionDO::getInspStatus, InspStatus.CANCELED.name()).last("LIMIT 1"));
            if (exist != null) return exist.getId();
        }
        MaterialDTO m = support.material(c.materialId());
        QcInspectionDO d = new QcInspectionDO();
        d.setDocNo(support.nextNo("QC_" + c.type().name()));
        d.setDocDate(LocalDate.now());
        d.setInspectType(c.type().name());
        d.setIpqcKind(c.type() == InspectType.IPQC ? (StringUtils.hasText(c.ipqcKind()) ? c.ipqcKind() : "REPORT") : null);
        d.setMaterialId(m.id());
        d.setBatchNo(QcSupport.trim(c.batchNo()));
        d.setLotQty(Decimals.qty(c.lotQty()));
        d.setSupplierId(c.supplierId());
        d.setCustomerId(c.customerId());
        d.setSourceType(c.sourceType());
        d.setSourceId(c.sourceId());
        d.setSourceNo(c.sourceNo());
        d.setSourceLineId(c.sourceLineId());
        d.setUpstreamType(c.upstreamType());
        d.setUpstreamId(c.upstreamId());
        d.setUpstreamLineId(c.upstreamLineId());
        d.setUpstreamNo(c.upstreamNo());
        d.setProdOrderId(c.prodOrderId());
        d.setOperationSeq(c.operationSeq());
        d.setWarehouseId(c.warehouseId());
        d.setRemark(QcSupport.limit(c.remark(), 512));
        d.setQualifiedQty(BigDecimal.ZERO);
        d.setConcessionQty(BigDecimal.ZERO);
        d.setRejectedQty(BigDecimal.ZERO);
        d.setPresetConcessionQty(BigDecimal.ZERO);
        d.setPresetRejectedQty(BigDecimal.ZERO);
        d.setCrCount(0);
        d.setMaCount(0);
        d.setMiCount(0);
        d.setMrbSort(false);
        d.setRejudgeCount(0);
        d.setOverdueNotified(false);
        d.setInspStatus(InspStatus.PENDING.name());
        d.setStatus(InspStatus.PENDING.docStatus());
        support.fillOwner(d, null);

        QcStandardDO std = standardService.match(m.id(), c.type(), c.operation());
        List<QcStandardItemDO> stdItems = List.of();
        SamplingResult sampling;
        if (std != null) {
            d.setStandardId(std.getId());
            d.setStandardCode(std.getCode());
            d.setStandardVersion(std.getStdVersion());
            sampling = SamplingService.compute(samplingService.get(std.getSamplingPlanId()), d.getLotQty());
            stdItems = standardService.items(std.getId());
        } else {
            QcSamplingPlanDO full = new QcSamplingPlanDO();
            full.setPlanType(SamplingService.FULL);
            full.setName("全检（无检验标准）");
            sampling = SamplingService.compute(full, d.getLotQty());
        }
        d.setSampleQty(sampling.sampleQty());
        d.setSamplingSnapshot(support.toJson(sampling));
        mapper.insert(d);

        Map<Long, QcSamplingPlanDO> overrides = samplingService.plans(stdItems.stream().map(QcStandardItemDO::getSamplingPlanId).toList());
        for (QcStandardItemDO si : stdItems) {
            QcInspectionItemDO it = new QcInspectionItemDO();
            it.setInspectionId(d.getId());
            it.setSeq(si.getSeq());
            it.setItemName(si.getName());
            it.setItemType(si.getItemType());
            it.setMethod(si.getMethod());
            it.setUnit(si.getUnit());
            it.setSpec(si.getSpec());
            it.setTarget(si.getTarget());
            it.setUpperLimit(si.getUpperLimit());
            it.setLowerLimit(si.getLowerLimit());
            it.setDefectLevel(si.getDefectLevel());
            it.setIsKey(Boolean.TRUE.equals(si.getIsKey()));
            it.setSampleQty(SamplingService.itemSampleQty(overrides.get(si.getSamplingPlanId()), d.getLotQty(), sampling.sampleQty()));
            it.setNgCount(0);
            itemMapper.insert(it);
        }
        support.log(QualityModuleConfig.INSPECTION, d.getId(), d.getDocNo(), QcAction.CREATE.name(), QcAction.CREATE.label(), null, d.getInspStatus(),
                c.upstreamNo() == null ? null : "来源 " + c.upstreamNo());
        eventPublisher.publish(new InspectionCreatedEvent(d.getId(), d.getDocNo(), d.getInspectType(), d.getMaterialId(), d.getBatchNo(), d.getLotQty(),
                d.getUpstreamType(), d.getUpstreamId(), d.getUpstreamNo()));
        if (exempt(c.type(), m.id()) || SamplingService.EXEMPT.equals(sampling.planType())) {
            QcInspectionDO fresh = get(d.getId());
            fresh.setSuggestedResult(PASS);
            doJudge(fresh, QUALIFIED, fresh.getLotQty(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "免检", QcAction.JUDGE);
        }
        return d.getId();
    }

    /** 物料质量属性免检（兜底：仓库不会把免检物料放入待检仓） */
    private boolean exempt(InspectType type, Long materialId) {
        MaterialQualityAttr a = support.materialApi().getQualityAttr(materialId);
        if (a == null) return false;
        return switch (type) {
            case IQC -> !a.iqcRequired();
            case FQC -> !a.fqcRequired();
            case OQC -> !a.oqcRequired();
            default -> false;
        };
    }

    // ==================== 结果录入 ====================

    /** 保存项目结果与缺陷；首次录入自动变为检验中（记录开始时间、检验员）；按 Ac/Re 计算建议结果 */
    @Transactional(rollbackFor = Exception.class)
    public void saveResults(Long id, ResultsSave req) {
        QcInspectionDO d = get(id);
        checkPermission(d, "inspect");
        InspStatus st = InspStatus.valueOf(d.getInspStatus());
        if (st != InspStatus.PENDING && st != InspStatus.INSPECTING) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, st.label(), "录入检验结果");
        if (req.version() != null) d.setVersion(req.version());
        List<QcInspectionItemDO> items = itemMapper.selectByParent(id);
        Map<Long, ItemSave> saves = new HashMap<>();
        if (req.items() != null) req.items().forEach(s -> saves.put(s.id(), s));
        for (QcInspectionItemDO it : items) {
            ItemSave s = saves.get(it.getId());
            if (s == null) continue;
            if (BasicDataService.QUANTITATIVE.equals(it.getItemType())) {
                List<BigDecimal> values = s.measuredValues() == null ? List.of() : s.measuredValues().stream().filter(Objects::nonNull).toList();
                if (values.size() > Math.max(it.getSampleQty(), 1)) throw BizException.of(QualityErrorCodes.INS_SAMPLE_VALUES, it.getItemName(), it.getSampleQty());
                it.setMeasuredValues(values.isEmpty() ? null : support.toJson(values));
                it.setNgCount((int) values.stream().filter(v -> outOfLimit(v, it.getLowerLimit(), it.getUpperLimit())).count());
                it.setItemResult(values.isEmpty() ? null : it.getNgCount() > 0 ? "NG" : "OK");
            } else {
                int ng = s.ngCount() == null ? 0 : Math.max(0, s.ngCount());
                it.setNgCount(ng);
                it.setItemResult(s.ngCount() == null ? null : ng > 0 ? "NG" : "OK");
            }
            it.setRemark(QcSupport.limit(s.remark(), 256));
            itemMapper.updateByIdOrFail(it);
        }
        defectMapper.deleteByParent(id);
        List<QcInspectionDefectDO> defects = new ArrayList<>();
        if (req.defects() != null && !req.defects().isEmpty()) {
            Map<String, QcDefectCodeDO> codes = basicDataService.defectsByCode(req.defects().stream().map(DefectSave::defectCode).toList());
            for (DefectSave s : req.defects()) {
                if (s.qty() == null || s.qty() <= 0) continue;
                QcDefectCodeDO code = codes.get(s.defectCode());
                if (code == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "缺陷代码 " + s.defectCode());
                QcInspectionDefectDO df = new QcInspectionDefectDO();
                df.setInspectionId(id);
                df.setDefectCode(code.getCode());
                df.setDefectName(code.getName());
                df.setDefectLevel(BasicDataService.level(StringUtils.hasText(s.defectLevel()) ? s.defectLevel() : code.getDefaultLevel()));
                df.setQty(s.qty());
                df.setDescription(QcSupport.limit(s.description(), 512));
                df.setImageFileIds(QcSupport.idText(s.imageFileIds()));
                defectMapper.insert(df);
                defects.add(df);
                if (s.imageFileIds() != null && !s.imageFileIds().isEmpty()) {
                    support.bindFiles(s.imageFileIds(), QualityModuleConfig.INSPECTION, id);
                }
            }
        }
        Map<String, Integer> counts = levelCounts(items, defects);
        d.setCrCount(counts.get("CR"));
        d.setMaCount(counts.get("MA"));
        d.setMiCount(counts.get("MI"));
        d.setSuggestedResult(suggest(d, counts));
        d.setInspectedAt(LocalDateTime.now());
        if (req.remark() != null) d.setRemark(QcSupport.limit(req.remark(), 512));
        if (st == InspStatus.PENDING) {
            d.setStartedAt(LocalDateTime.now());
            d.setInspectorId(support.currentUser());
            fire(d, QcAction.START, null);
        } else {
            if (d.getInspectorId() == null) d.setInspectorId(support.currentUser());
            mapper.updateByIdOrFail(d);
        }
    }

    static boolean outOfLimit(BigDecimal v, BigDecimal lower, BigDecimal upper) {
        return (lower != null && v.compareTo(lower) < 0) || (upper != null && v.compareTo(upper) > 0);
    }

    /** 各等级缺陷数：项目不良数合计与缺陷明细合计取较大者 */
    static Map<String, Integer> levelCounts(List<QcInspectionItemDO> items, List<QcInspectionDefectDO> defects) {
        Map<String, Integer> result = new HashMap<>();
        for (String lv : BasicDataService.LEVELS) {
            int itemNg = items.stream().filter(i -> lv.equals(i.getDefectLevel())).mapToInt(i -> i.getNgCount() == null ? 0 : i.getNgCount()).sum();
            int defectSum = defects.stream().filter(x -> lv.equals(x.getDefectLevel())).mapToInt(QcInspectionDefectDO::getQty).sum();
            result.put(lv, Math.max(itemNg, defectSum));
        }
        return result;
    }

    /** 任一等级缺陷数 ≥ Re 判不合格 */
    String suggest(QcInspectionDO d, Map<String, Integer> counts) {
        SamplingResult s = sampling(d);
        if (s == null || s.levels() == null) return PASS;
        for (LevelPlan lp : s.levels()) {
            if (counts.getOrDefault(lp.level(), 0) >= lp.re()) return FAIL;
        }
        return PASS;
    }

    public SamplingResult sampling(QcInspectionDO d) {
        return support.fromJson(d.getSamplingSnapshot(), new TypeReference<SamplingResult>() {
        });
    }

    // ==================== 判定 ====================

    /** QC-INS-R03、R05：合格 / 拒收 / 挑选；特采只能通过 MRB */
    @Transactional(rollbackFor = Exception.class)
    public void judge(Long id, JudgeReq req) {
        QcInspectionDO d = get(id);
        checkPermission(d, "judge");
        InspStatus st = InspStatus.valueOf(d.getInspStatus());
        if (st != InspStatus.PENDING && st != InspStatus.INSPECTING) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, st.label(), "判定");
        BigDecimal lot = d.getLotQty();
        String result = req.result();
        boolean sort = Boolean.TRUE.equals(d.getMrbSort());
        switch (result) {
            case CONCESSION -> throw new BizException(QualityErrorCodes.INS_CONCESSION_MRB);
            case QUALIFIED, REJECTED -> {
                if (sort) throw new BizException(QualityErrorCodes.INS_SORT_ONLY);
                String reason = QcSupport.trim(req.reason());
                if (QUALIFIED.equals(result) && FAIL.equals(d.getSuggestedResult()) && reason == null) throw new BizException(QualityErrorCodes.INS_PASS_REASON);
                doJudge(d, result, QUALIFIED.equals(result) ? lot : BigDecimal.ZERO, BigDecimal.ZERO, REJECTED.equals(result) ? lot : BigDecimal.ZERO,
                        BigDecimal.ZERO, reason, QcAction.JUDGE);
            }
            case SORTED -> {
                BigDecimal q = QcSupport.nz(req.qualifiedQty());
                BigDecimal r = QcSupport.nz(req.rejectedQty());
                BigDecimal c = QcSupport.nz(d.getPresetConcessionQty());
                if (q.signum() < 0 || r.signum() < 0 || q.add(r).add(c).compareTo(lot) != 0) throw BizException.of(QualityErrorCodes.INS_JUDGE_QTY, QcSupport.plain(lot));
                if (r.compareTo(QcSupport.nz(d.getPresetRejectedQty())) < 0) {
                    throw BizException.of(QualityErrorCodes.INS_REJECTED_LOCKED, QcSupport.plain(d.getPresetRejectedQty()));
                }
                doJudge(d, SORTED, q, c, r, BigDecimal.ZERO, QcSupport.trim(req.reason()), QcAction.JUDGE);
            }
            default -> throw BizException.of(QualityErrorCodes.NOT_EXISTS, "判定结果 " + result);
        }
    }

    /** 批量判定合格：仅对已录入（检验中）且建议结果为 PASS 的单据，逐条独立事务 */
    public BatchJudgeResult batchJudgePass(List<Long> ids) {
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            QcInspectionDO d = mapper.selectById(id);
            if (d == null) continue;
            if (!InspStatus.INSPECTING.name().equals(d.getInspStatus()) || !PASS.equals(d.getSuggestedResult()) || Boolean.TRUE.equals(d.getMrbSort())) {
                errors.add(d.getDocNo() + "：" + QualityErrorCodes.INS_BATCH_JUDGE_SKIP.message());
                continue;
            }
            try {
                tx.executeWithoutResult(s -> judge(id, new JudgeReq(QUALIFIED, null, null, null)));
                ok++;
            } catch (BizException e) {
                errors.add(d.getDocNo() + "：" + e.getMessage());
            }
        }
        return new BatchJudgeResult(ok, errors);
    }

    /**
     * 完成判定：保存数量与判定人 → 状态已判定 → 检验调拨（涉及库存的类型）→ 回写上游 → 发布判定事件 → 拒收自动生成 NCR（参数）。
     *
     * @param reworkQty 退货检验：不合格中需返工的数量（MRB 处置为返工），其余不合格按报废回写销售退货
     */
    void doJudge(QcInspectionDO d, String result, BigDecimal qualified, BigDecimal concession, BigDecimal rejected, BigDecimal reworkQty,
                 String reason, QcAction action) {
        d.setResult(result);
        d.setQualifiedQty(Decimals.qty(qualified));
        d.setConcessionQty(Decimals.qty(concession));
        d.setRejectedQty(Decimals.qty(rejected));
        d.setJudgeBy(support.currentUser());
        d.setJudgeAt(LocalDateTime.now());
        d.setJudgeReason(QcSupport.limit(reason, 512));
        if (d.getInspectorId() == null) d.setInspectorId(support.currentUser());
        if (d.getInspectedAt() == null) d.setInspectedAt(LocalDateTime.now());
        d.setTransferIds(null);
        d.setConfirmedTransferIds(null);
        fire(d, action, resultLabel(result) + (reason == null ? "" : "：" + reason));
        InspectType type = InspectType.valueOf(d.getInspectType());

        if (type.stockAction() && d.getWarehouseId() != null) {
            List<TransferRequest.Line> lines = new ArrayList<>();
            if (d.getQualifiedQty().signum() > 0) lines.add(transferLine(d, d.getQualifiedQty(), JudgeResult.QUALIFIED));
            if (d.getConcessionQty().signum() > 0) lines.add(transferLine(d, d.getConcessionQty(), JudgeResult.CONCESSION));
            if (d.getRejectedQty().signum() > 0) lines.add(transferLine(d, d.getRejectedQty(), JudgeResult.REJECTED));
            List<Long> transferIds = inventoryDocApi.createTransfer(new TransferRequest(TransferType.INSPECTION, new SourceRef(TRANSFER_SOURCE, d.getId(), d.getDocNo()),
                    d.getId(), d.getWarehouseId(), null, LocalDate.now(), "检验判定 " + d.getDocNo(), lines));
            QcInspectionDO fresh = get(d.getId());
            fresh.setTransferIds(QcSupport.idText(transferIds));
            mapper.updateByIdOrFail(fresh);
            checkHandled(fresh);
        }
        writeBack(get(d.getId()), reworkQty);
        eventPublisher.publish(new InspectionJudgedEvent(d.getId(), d.getDocNo(), d.getInspectType(), d.getMaterialId(), d.getBatchNo(), d.getUpstreamType(),
                d.getUpstreamId(), d.getUpstreamLineId(), d.getUpstreamNo(), d.getLotQty(), d.getQualifiedQty(), d.getConcessionQty(), d.getRejectedQty(), result));
        support.resolve("QC_INS_OVERDUE_" + d.getId());
        if (REJECTED.equals(result) && d.getNcrId() == null && support.params().getBool(QualityModuleConfig.P_AUTO_NCR)) {
            QcInspectionDO fresh = get(d.getId());
            Long ncrId = ncrService.getObject().createFromInspection(fresh, fresh.getRejectedQty(), false);
            fresh = get(d.getId());
            fresh.setNcrId(ncrId);
            mapper.updateByIdOrFail(fresh);
        }
    }

    private static TransferRequest.Line transferLine(QcInspectionDO d, BigDecimal qty, JudgeResult r) {
        return new TransferRequest.Line(d.getSourceLineId(), d.getMaterialId(), d.getBatchNo(), null, qty, r, null);
    }

    /** QC-INS-R04：IQC → 到货行；FQC → 完工入库申请；退货检验 → 销售退货行 */
    private void writeBack(QcInspectionDO d, BigDecimal reworkQty) {
        switch (InspectType.valueOf(d.getInspectType())) {
            case IQC -> {
                if ("PUR_RECEIPT".equals(d.getUpstreamType()) && d.getUpstreamLineId() != null) {
                    purchaseReceiptApi.applyInspection(d.getUpstreamLineId(), d.getDocNo(), d.getQualifiedQty(), d.getConcessionQty(), d.getRejectedQty());
                }
            }
            case FQC -> {
                if (ProductionFinishApi.SOURCE_TYPE.equals(d.getUpstreamType()) && d.getUpstreamId() != null) {
                    productionFinishApi.onFqcJudged(d.getUpstreamId(), d.getQualifiedQty().add(d.getConcessionQty()), d.getRejectedQty());
                }
            }
            case RETURN -> {
                if ("SAL_RETURN".equals(d.getUpstreamType()) && d.getUpstreamLineId() != null) {
                    BigDecimal rework = QcSupport.nz(reworkQty).min(d.getRejectedQty());
                    salesReturnApi.recordJudgement(d.getUpstreamLineId(), d.getQualifiedQty().add(d.getConcessionQty()), rework, d.getRejectedQty().subtract(rework));
                }
            }
            default -> {
            }
        }
    }

    /** 撤销回写（重判） */
    private void revertWriteBack(QcInspectionDO d) {
        switch (InspectType.valueOf(d.getInspectType())) {
            case IQC -> {
                if ("PUR_RECEIPT".equals(d.getUpstreamType()) && d.getUpstreamLineId() != null) purchaseReceiptApi.revertInspection(d.getUpstreamLineId());
            }
            case FQC -> {
                if (ProductionFinishApi.SOURCE_TYPE.equals(d.getUpstreamType()) && d.getUpstreamId() != null) {
                    productionFinishApi.onFqcJudged(d.getUpstreamId(), d.getQualifiedQty().add(d.getConcessionQty()).negate(), d.getRejectedQty().negate());
                }
            }
            case RETURN -> {
                if ("SAL_RETURN".equals(d.getUpstreamType()) && d.getUpstreamLineId() != null) {
                    salesReturnApi.recordJudgement(d.getUpstreamLineId(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                }
            }
            default -> {
            }
        }
    }

    public static String resultLabel(String result) {
        if (result == null) return "";
        return switch (result) {
            case QUALIFIED -> "合格";
            case REJECTED -> "拒收";
            case CONCESSION -> "特采";
            case SORTED -> "挑选";
            default -> result;
        };
    }

    // ==================== MRB ====================

    /** 提交 MRB：生成 NCR（草稿，数量 = 批量），检验单待 MRB */
    @Transactional(rollbackFor = Exception.class)
    public Long toMrb(Long id) {
        QcInspectionDO d = get(id);
        checkPermission(d, "judge");
        InspStatus st = InspStatus.valueOf(d.getInspStatus());
        if (st != InspStatus.PENDING && st != InspStatus.INSPECTING) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, st.label(), "提交 MRB");
        Long ncrId = ncrService.getObject().createFromInspection(d, d.getLotQty(), true);
        d = get(id);
        d.setNcrId(ncrId);
        d.setMrbSort(false);
        fire(d, QcAction.TO_MRB, null);
        return ncrId;
    }

    /**
     * NCR 审批通过（QC-NCR-R03）：特采 → 特采数量；退货 / 返工 / 报废 → 不合格数量；NCR 以外的数量为合格；
     * 含挑选时检验单回到检验中，只能按“挑选”判定（特采、不合格数量已锁定）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyMrb(Long inspectionId, String ncrNo, BigDecimal ncrQty, BigDecimal concession, BigDecimal rejected, BigDecimal sort, BigDecimal rework) {
        QcInspectionDO d = get(inspectionId);
        if (!InspStatus.WAIT_MRB.name().equals(d.getInspStatus())) return;
        if (sort.signum() > 0) {
            d.setMrbSort(true);
            d.setPresetConcessionQty(Decimals.qty(concession));
            d.setPresetRejectedQty(Decimals.qty(rejected));
            fire(d, QcAction.MRB_SORT, "NCR " + ncrNo + " 处置含挑选 " + QcSupport.plain(sort));
            return;
        }
        BigDecimal qualified = d.getLotQty().subtract(QcSupport.nz(ncrQty)).max(BigDecimal.ZERO);
        String result = concession.signum() > 0 ? CONCESSION : qualified.signum() > 0 ? SORTED : REJECTED;
        doJudge(d, result, qualified, concession, d.getLotQty().subtract(qualified).subtract(concession).max(BigDecimal.ZERO), rework, "MRB " + ncrNo,
                QcAction.MRB_JUDGE);
    }

    /** NCR 作废：检验单退回检验中 */
    @Transactional(rollbackFor = Exception.class)
    public void mrbBack(Long inspectionId, String ncrNo) {
        QcInspectionDO d = get(inspectionId);
        if (!InspStatus.WAIT_MRB.name().equals(d.getInspStatus())) return;
        d.setNcrId(null);
        fire(d, QcAction.MRB_BACK, "NCR " + ncrNo + " 作废");
    }

    // ==================== 重判 ====================

    /** QC-INS-R06：已判定、检验调拨未确认时发起重判（审批流 QC_REJUDGE），未配置审批时直接重判 */
    @Transactional(rollbackFor = Exception.class)
    public void rejudge(Long id, String reason) {
        QcInspectionDO d = get(id);
        String r = QcSupport.requireText(reason, "重判原因");
        if (InspStatus.HANDLED.name().equals(d.getInspStatus()) || StringUtils.hasText(d.getConfirmedTransferIds())) {
            throw new BizException(QualityErrorCodes.INS_TRANSFER_DONE);
        }
        if (!InspStatus.JUDGED.name().equals(d.getInspStatus())) throw new BizException(QualityErrorCodes.INS_NOT_JUDGED);
        if (workflowApi.isRunning(QualityModuleConfig.REJUDGE, id)) throw new BizException(QualityErrorCodes.DOC_PENDING);
        d.setRejudgeReason(QcSupport.limit(r, 512));
        mapper.updateByIdOrFail(d);
        StartResult sr = workflowApi.start(QualityModuleConfig.REJUDGE, id, d.getDocNo(), "检验重判 " + d.getDocNo(), Map.of("inspectType", d.getInspectType()),
                Map.of(), support.currentUser());
        support.log(QualityModuleConfig.INSPECTION, id, d.getDocNo(), QcAction.REJUDGE.name(), "发起重判", d.getInspStatus(), d.getInspStatus(), r);
        if (!sr.isStarted()) doRejudge(id);
    }

    /** 重判生效：作废未确认的检验调拨、撤销上游回写、清空判定，回到检验中 */
    @Transactional(rollbackFor = Exception.class)
    public void doRejudge(Long id) {
        QcInspectionDO d = get(id);
        if (InspStatus.HANDLED.name().equals(d.getInspStatus()) || StringUtils.hasText(d.getConfirmedTransferIds())) {
            throw new BizException(QualityErrorCodes.INS_TRANSFER_DONE);
        }
        if (!InspStatus.JUDGED.name().equals(d.getInspStatus())) return;
        if (StringUtils.hasText(d.getTransferIds())) inventoryDocApi.cancelBySource(TRANSFER_SOURCE, id);
        revertWriteBack(d);
        d = get(id);
        String previous = d.getResult();
        d.setResult(null);
        d.setQualifiedQty(BigDecimal.ZERO);
        d.setConcessionQty(BigDecimal.ZERO);
        d.setRejectedQty(BigDecimal.ZERO);
        d.setJudgeBy(null);
        d.setJudgeAt(null);
        d.setJudgeReason(null);
        d.setTransferIds(null);
        d.setConfirmedTransferIds(null);
        d.setMrbSort(false);
        d.setPresetConcessionQty(BigDecimal.ZERO);
        d.setPresetRejectedQty(BigDecimal.ZERO);
        d.setRejudgeCount(d.getRejudgeCount() == null ? 1 : d.getRejudgeCount() + 1);
        fire(d, QcAction.REJUDGE, d.getRejudgeReason());
        eventPublisher.publish(new InspectionRejudgedEvent(d.getId(), d.getDocNo(), d.getInspectType(), d.getUpstreamType(), d.getUpstreamId(),
                d.getUpstreamLineId(), previous));
    }

    // ==================== 来源撤销、调拨完成 ====================

    /** QC-INS-R07：入库单反确认前校验（已判定的阻止） */
    public void checkSourceReversible(String sourceType, Long sourceId) {
        for (QcInspectionDO d : bySource(sourceType, sourceId)) {
            InspStatus st = InspStatus.valueOf(d.getInspStatus());
            if (st == InspStatus.WAIT_MRB || st == InspStatus.JUDGED || st == InspStatus.HANDLED) throw new BizException(QualityErrorCodes.INS_JUDGED_REVERSE);
        }
    }

    /** 来源撤销：待检 / 检验中的检验单自动取消 */
    @Transactional(rollbackFor = Exception.class)
    public int cancelBySource(String sourceType, Long sourceId, String reason) {
        int n = 0;
        for (QcInspectionDO d : bySource(sourceType, sourceId)) {
            InspStatus st = InspStatus.valueOf(d.getInspStatus());
            if (st == InspStatus.PENDING || st == InspStatus.INSPECTING) {
                fire(d, QcAction.CANCEL, reason);
                support.resolve("QC_INS_OVERDUE_" + d.getId());
                n++;
            }
        }
        return n;
    }

    private List<QcInspectionDO> bySource(String sourceType, Long sourceId) {
        return mapper.selectList(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getSourceType, sourceType).eq(QcInspectionDO::getSourceId, sourceId)
                .ne(QcInspectionDO::getInspStatus, InspStatus.CANCELED.name()));
    }

    /** 检验调拨确认：全部调拨单确认后检验单“已处理” */
    @Transactional(rollbackFor = Exception.class)
    public void onTransferConfirmed(Long inspectionId, Long transferId) {
        QcInspectionDO d = mapper.selectById(inspectionId);
        if (d == null) return;
        Set<Long> confirmed = new LinkedHashSet<>(QcSupport.ids(d.getConfirmedTransferIds()));
        if (!confirmed.add(transferId)) return;
        d.setConfirmedTransferIds(QcSupport.idText(confirmed));
        mapper.updateByIdOrFail(d);
        checkHandled(get(inspectionId));
    }

    private void checkHandled(QcInspectionDO d) {
        if (!InspStatus.JUDGED.name().equals(d.getInspStatus())) return;
        List<Long> all = QcSupport.ids(d.getTransferIds());
        if (all.isEmpty()) return;
        if (QcSupport.ids(d.getConfirmedTransferIds()).containsAll(all)) fire(d, QcAction.HANDLE, null);
    }

    // ==================== 通用 ====================

    public QcInspectionDO get(Long id) {
        QcInspectionDO d = id == null ? null : mapper.selectById(id);
        if (d == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "检验单");
        return d;
    }

    /** 权限点的类型段：复检归 IQC */
    public static String permSegment(String inspectType) {
        return switch (InspectType.valueOf(inspectType)) {
            case IQC, RECHECK -> "iqc";
            case IPQC -> "ipqc";
            case FQC -> "fqc";
            case OQC -> "oqc";
            case RETURN -> "return";
        };
    }

    void checkPermission(QcInspectionDO d, String op) {
        String perm = "qc:" + permSegment(d.getInspectType()) + ":" + op;
        if (!support.hasPermission(perm)) {
            throw BizException.of(QualityErrorCodes.INS_NO_PERMISSION, InspectType.valueOf(d.getInspectType()).label(), "judge".equals(op) ? "判定" : "录入");
        }
    }

    private void fire(QcInspectionDO d, QcAction action, String reason) {
        InspStatus from = InspStatus.valueOf(d.getInspStatus());
        InspStatus to = QcStateMachines.INSPECTION.fire(from, action);
        d.setInspStatus(to.name());
        d.setStatus(to.docStatus());
        mapper.updateByIdOrFail(d);
        support.log(QualityModuleConfig.INSPECTION, d.getId(), d.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }
}
