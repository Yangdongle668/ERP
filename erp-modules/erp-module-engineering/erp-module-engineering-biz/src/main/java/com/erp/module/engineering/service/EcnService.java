package com.erp.module.engineering.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocAction;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.DocStateMachines;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.ecn.EcnApprovedEvent;
import com.erp.module.engineering.api.ecn.EcnEffectiveEvent;
import com.erp.module.engineering.api.ecn.EcnImpact;
import com.erp.module.engineering.api.ecn.EcnImpactProvider;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.controller.vo.EcnVOs.BatchReplaceReq;
import com.erp.module.engineering.controller.vo.EcnVOs.EcnDetail;
import com.erp.module.engineering.controller.vo.EcnVOs.EcnQuery;
import com.erp.module.engineering.controller.vo.EcnVOs.EcnRow;
import com.erp.module.engineering.controller.vo.EcnVOs.EcnSave;
import com.erp.module.engineering.controller.vo.EcnVOs.ImpactResp;
import com.erp.module.engineering.controller.vo.EcnVOs.ImpactSave;
import com.erp.module.engineering.controller.vo.EcnVOs.LineResp;
import com.erp.module.engineering.controller.vo.EcnVOs.LineSave;
import com.erp.module.engineering.controller.vo.EcnVOs.SubmitResult;
import com.erp.module.engineering.controller.vo.EcnVOs.TaskResp;
import com.erp.module.engineering.controller.vo.EcnVOs.TaskSave;
import com.erp.module.engineering.dal.dataobject.BomDO;
import com.erp.module.engineering.dal.dataobject.BomLineDO;
import com.erp.module.engineering.dal.dataobject.BomSubstituteDO;
import com.erp.module.engineering.dal.dataobject.CertificationMaterialDO;
import com.erp.module.engineering.dal.dataobject.EcnDO;
import com.erp.module.engineering.dal.dataobject.EcnImpactDO;
import com.erp.module.engineering.dal.dataobject.EcnLineDO;
import com.erp.module.engineering.dal.dataobject.EcnTaskDO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.mapper.BomLineMapper;
import com.erp.module.engineering.dal.mapper.BomMapper;
import com.erp.module.engineering.dal.mapper.BomSubstituteMapper;
import com.erp.module.engineering.dal.mapper.CertificationMaterialMapper;
import com.erp.module.engineering.dal.mapper.EcnImpactMapper;
import com.erp.module.engineering.dal.mapper.EcnLineMapper;
import com.erp.module.engineering.dal.mapper.EcnMapper;
import com.erp.module.engineering.dal.mapper.EcnTaskMapper;
import com.erp.module.inventory.api.stock.InventoryQueryApi;
import com.erp.module.inventory.api.stock.StockSummary;
import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.notify.TodoCreatedEvent;
import com.erp.module.system.api.notify.TodoDoneEvent;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ECN 工程变更（需求 05-05）：草稿 →（影响分析）→ 提交审批 ENG_ECN → 已审核（生成新 BOM 版本）→ 生效（新版本设为默认）→ 执行确认 → 关闭。
 * 通用状态：已审核 APPROVED、已生效 IN_PROGRESS、已关闭 COMPLETED。
 * 在途采购、在制生产订单、未完成销售订单的影响由各模块实现 {@link EcnImpactProvider}；库存直接查询仓库。
 */
@Service
public class EcnService {

    public static final String BIZ_TYPE = "ENG_ECN";
    static final String CODE_RULE = "ENG_ECN";
    static final Set<String> ACTIONS = Set.of("ADD", "REMOVE", "REPLACE", "CHANGE_QTY");
    static final Set<String> MODES = Set.of("IMMEDIATE", "DATE", "USE_UP");
    static final Map<String, Set<String>> HANDLINGS = Map.of(
            "STOCK", Set.of("CONTINUE_USE", "REWORK", "SCRAP", "RETURN_SUPPLIER"),
            "PURCHASE", Set.of("CANCEL", "KEEP"),
            "WIP", Set.of("UPDATE_WIP", "KEEP"),
            "SALES", Set.of("NO_ACTION"));
    static final Set<String> DEPT_ROLES = Set.of("PURCHASE", "WAREHOUSE", "PRODUCTION", "QUALITY", "PMC", "CERT");
    static final String KEY_PART_WARNING = "该变更涉及认证关键件，请评估是否需要重新认证";
    private static final List<DocStatus> LOCKING = List.of(DocStatus.PENDING_APPROVAL, DocStatus.APPROVED);

    private final EcnMapper mapper;
    private final EcnLineMapper lineMapper;
    private final EcnImpactMapper impactMapper;
    private final EcnTaskMapper taskMapper;
    private final BomMapper bomMapper;
    private final BomLineMapper bomLineMapper;
    private final BomSubstituteMapper substituteMapper;
    private final CertificationMaterialMapper certMaterialMapper;
    private final BomService bomService;
    private final BomQueryService bomQueryService;
    private final EngSupport support;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final NotifyApi notifyApi;
    private final DictApi dictApi;
    private final InventoryQueryApi inventoryQueryApi;
    private final DomainEventPublisher eventPublisher;
    private final List<EcnImpactProvider> impactProviders;
    private final TransactionTemplate tx;

    public EcnService(EcnMapper mapper, EcnLineMapper lineMapper, EcnImpactMapper impactMapper, EcnTaskMapper taskMapper, BomMapper bomMapper,
                      BomLineMapper bomLineMapper, BomSubstituteMapper substituteMapper, CertificationMaterialMapper certMaterialMapper,
                      BomService bomService, BomQueryService bomQueryService, EngSupport support, WorkflowApi workflowApi, FileApi fileApi,
                      NotifyApi notifyApi, DictApi dictApi, InventoryQueryApi inventoryQueryApi, DomainEventPublisher eventPublisher,
                      List<EcnImpactProvider> impactProviders, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.impactMapper = impactMapper;
        this.taskMapper = taskMapper;
        this.bomMapper = bomMapper;
        this.bomLineMapper = bomLineMapper;
        this.substituteMapper = substituteMapper;
        this.certMaterialMapper = certMaterialMapper;
        this.bomService = bomService;
        this.bomQueryService = bomQueryService;
        this.support = support;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.notifyApi = notifyApi;
        this.dictApi = dictApi;
        this.inventoryQueryApi = inventoryQueryApi;
        this.eventPublisher = eventPublisher;
        this.impactProviders = impactProviders;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(EcnSave req) {
        EcnDO e = new EcnDO();
        e.setDocNo(support.nextNo(CODE_RULE));
        e.setDocDate(LocalDate.now());
        e.setStatus(DocStatus.DRAFT);
        e.setOwnerId(support.currentUser());
        e.setAnalyzed(false);
        e.setKeyPart(false);
        fillHeader(e, req);
        mapper.insert(e);
        saveLines(e, req.lines());
        if (req.tasks() != null) saveTasks(e, req.tasks());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, e.getId());
        return e.getId();
    }

    /** 修改（仅草稿）：明细变化后影响分析需重新执行 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, EcnSave req) {
        EcnDO e = getOrThrow(id);
        if (e.getStatus() != DocStatus.DRAFT) throw new BizException(EngineeringErrorCodes.ECN_NOT_EDITABLE);
        if (req.version() != null) e.setVersion(req.version());
        fillHeader(e, req);
        String before = signature(lineMapper.selectByParent(id));
        saveLines(e, req.lines());
        if (!before.equals(signature(lineMapper.selectByParent(id)))) e.setAnalyzed(false);
        mapper.updateByIdOrFail(e);
        if (req.impacts() != null) saveImpacts(id, req.impacts());
        if (req.tasks() != null) saveTasks(e, req.tasks());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, id);
    }

    private void fillHeader(EcnDO e, EcnSave req) {
        dictApi.validate("eng_ecn_type", req.ecnType(), "变更类型");
        dictApi.validate("eng_ecn_reason", req.reasonType(), "变更原因");
        String mode = StringUtils.hasText(req.effectiveMode()) ? req.effectiveMode() : "IMMEDIATE";
        if (!MODES.contains(mode)) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "生效方式");
        if ("DATE".equals(mode) && (req.effectiveDate() == null || req.effectiveDate().isBefore(LocalDate.now()))) {
            throw new BizException(EngineeringErrorCodes.ECN_EFFECTIVE_DATE);
        }
        e.setTitle(req.title().trim());
        e.setEcnType(req.ecnType());
        e.setReasonType(req.reasonType());
        e.setReason(req.reason().trim());
        e.setUrgency("URGENT".equals(req.urgency()) ? "URGENT" : "NORMAL");
        e.setEffectiveMode(mode);
        e.setEffectiveDate("DATE".equals(mode) ? req.effectiveDate() : null);
        e.setCustomerId(req.customerId());
    }

    /** 保存明细：校验动作与必填项、原子件必须在 BOM 中、新子件必须启用；原用量/原损耗从 BOM 带出 */
    private void saveLines(EcnDO e, List<LineSave> lines) {
        lineMapper.deleteByParent(e.getId());
        if (lines == null) return;
        Map<Long, BomDO> boms = new HashMap<>();
        Map<Long, List<BomLineDO>> bomLines = new HashMap<>();
        Map<Long, MaterialDO> ms = support.materials(lines.stream().map(LineSave::newComponentId).filter(Objects::nonNull).toList());
        int no = 0;
        for (LineSave l : lines) {
            no++;
            BomDO bom = boms.computeIfAbsent(l.bomId(), bomMapper::selectById);
            if (bom == null) throw lineError(no, "BOM 不存在");
            List<BomLineDO> bls = bomLines.computeIfAbsent(bom.getId(), bomLineMapper::selectByBom);
            if (!ACTIONS.contains(l.action())) throw lineError(no, "变更动作不正确");
            boolean needOld = !"ADD".equals(l.action());
            boolean needNew = "ADD".equals(l.action()) || "REPLACE".equals(l.action());
            BomLineDO old = null;
            if (needOld) {
                if (l.oldComponentId() == null) throw lineError(no, "请选择原子件");
                old = bls.stream().filter(x -> x.getComponentId().equals(l.oldComponentId())).findFirst().orElse(null);
                if (old == null) throw lineError(no, "原子件不在 BOM「" + bom.getDocNo() + "」中");
            }
            MaterialDO nm = null;
            if (needNew) {
                if (l.newComponentId() == null) throw lineError(no, "请选择新子件");
                nm = ms.get(l.newComponentId());
                if (nm == null || nm.getStatus() != MaterialStatus.ENABLED) throw lineError(no, "新子件必须是启用的物料");
                if (nm.getId().equals(bom.getMaterialId())) throw lineError(no, "新子件不能是父件本身");
            }
            if (!"REMOVE".equals(l.action()) && (l.newQtyPer() == null || l.newQtyPer().signum() <= 0)) throw lineError(no, "请填写大于 0 的新用量");
            if (l.newScrapRate() != null && (l.newScrapRate().signum() < 0 || l.newScrapRate().compareTo(BigDecimal.ONE) >= 0)) {
                throw lineError(no, "损耗率必须在 0～100% 之间");
            }
            EcnLineDO x = new EcnLineDO();
            x.setEcnId(e.getId());
            x.setLineNo(no);
            x.setBomId(bom.getId());
            x.setAction(l.action());
            x.setOldComponentId(needOld ? l.oldComponentId() : null);
            x.setNewComponentId(needNew ? l.newComponentId() : null);
            x.setOldQtyPer(old == null ? null : old.getQtyPer());
            x.setOldScrapRate(old == null ? null : old.getScrapRate());
            x.setNewQtyPer("REMOVE".equals(l.action()) ? null : l.newQtyPer());
            x.setNewScrapRate("REMOVE".equals(l.action()) ? null : l.newScrapRate());
            x.setPositionNo(EngSupport.trim(l.positionNo()));
            x.setRemark(EngSupport.trim(l.remark()));
            lineMapper.insert(x);
        }
    }

    private static BizException lineError(int no, String msg) {
        return BizException.of(EngineeringErrorCodes.ECN_LINE_INVALID, no, msg);
    }

    private static String signature(List<EcnLineDO> lines) {
        return lines.stream().map(l -> l.getBomId() + "|" + l.getAction() + "|" + l.getOldComponentId() + "|" + l.getNewComponentId())
                .sorted().collect(Collectors.joining(";"));
    }

    private void saveImpacts(Long ecnId, List<ImpactSave> impacts) {
        Map<Long, EcnImpactDO> existing = impactMapper.selectByParent(ecnId).stream().collect(Collectors.toMap(EcnImpactDO::getId, x -> x));
        for (ImpactSave s : impacts) {
            EcnImpactDO x = existing.get(s.id());
            if (x == null) continue;
            String h = EngSupport.trim(s.handling());
            if (h != null && !HANDLINGS.getOrDefault(x.getImpactType(), Set.of()).contains(h)) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "处理方式");
            x.setHandling(h);
            x.setHandlingRemark(EngSupport.trim(s.handlingRemark()));
            impactMapper.updateByIdOrFail(x);
        }
    }

    private void saveTasks(EcnDO e, List<TaskSave> tasks) {
        taskMapper.deleteByParent(e.getId());
        for (TaskSave t : tasks) {
            if (!DEPT_ROLES.contains(t.deptRole())) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "执行部门");
            if (t.assigneeId() != null && !support.userExists(t.assigneeId())) throw new BizException(GlobalErrorCodes.DATA_NOT_EXISTS);
            insertTask(e.getId(), t.deptRole(), t.assigneeId(), t.content().trim());
        }
    }

    private void insertTask(Long ecnId, String deptRole, Long assigneeId, String content) {
        EcnTaskDO t = new EcnTaskDO();
        t.setEcnId(ecnId);
        t.setDeptRole(deptRole);
        t.setAssigneeId(assigneeId);
        t.setContent(content.length() > 512 ? content.substring(0, 512) : content);
        t.setTaskStatus("PENDING");
        taskMapper.insert(t);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        EcnDO e = getOrThrow(id);
        if (e.getStatus() != DocStatus.DRAFT) throw new BizException(EngineeringErrorCodes.ECN_NOT_EDITABLE);
        lineMapper.deleteByParent(id);
        impactMapper.deleteByParent(id);
        taskMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidEcn(Long id, String reason) {
        EcnDO e = getOrThrow(id);
        requireStatus(e, "作废", DocStatus.DRAFT);
        fire(e, DocAction.VOID, reason);
    }

    // ==================== 批量替换 ====================

    /** 批量替换预览：所有默认且已审核的 BOM 中使用原子件的行，生成 REPLACE 明细（未保存） */
    public List<LineResp> batchReplacePreview(BatchReplaceReq req) {
        MaterialDO nm = support.material(req.newComponentId());
        if (nm.getStatus() != MaterialStatus.ENABLED) throw BizException.of(EngineeringErrorCodes.ECN_LINE_INVALID, 1, "新子件必须是启用的物料");
        MaterialDO om = support.material(req.oldComponentId());
        List<BomLineDO> used = bomLineMapper.selectByComponent(om.getId());
        if (used.isEmpty()) return List.of();
        List<BomDO> boms = bomMapper.selectBatchIds(used.stream().map(BomLineDO::getBomId).collect(Collectors.toSet())).stream()
                .filter(b -> b.getStatus() == DocStatus.APPROVED && Boolean.TRUE.equals(b.getIsDefault()))
                .filter(b -> !b.getMaterialId().equals(nm.getId()))
                .sorted((a, b) -> a.getDocNo().compareTo(b.getDocNo())).toList();
        Map<Long, MaterialDO> parents = support.materials(boms.stream().map(BomDO::getMaterialId).toList());
        List<LineResp> out = new ArrayList<>();
        int no = 0;
        for (BomDO b : boms) {
            BomLineDO l = used.stream().filter(x -> x.getBomId().equals(b.getId())).findFirst().orElseThrow();
            MaterialDO p = parents.get(b.getMaterialId());
            BigDecimal qty = req.newQtyPer() != null ? req.newQtyPer() : l.getQtyPer();
            out.add(new LineResp(null, ++no, b.getId(), b.getDocNo(), p.getId(), p.getCode(), p.getName(), "REPLACE", om.getId(), om.getCode(),
                    om.getName(), nm.getId(), nm.getCode(), nm.getName(), om.getBaseUom(), l.getQtyPer(), qty, l.getScrapRate(), l.getScrapRate(),
                    l.getPositionNo(), null, null, null));
        }
        return out;
    }

    // ==================== 影响分析 ====================

    /**
     * 分析影响：旧子件的库存（仓库）、在途采购/在制生产订单/未完成销售订单（各模块扩展点）；保留已选择的处理方式。
     * 按影响自动生成执行任务（库存 → 仓库、在途采购 → 采购、在制 → 生产、销售 → PMC，品质默认“确认检验标准”，涉及认证关键件 → 认证评估），
     * 同一部门已有的负责人保留。
     */
    @Transactional(rollbackFor = Exception.class)
    public void analyze(Long id) {
        EcnDO e = getOrThrow(id);
        if (e.getStatus() != DocStatus.DRAFT) throw new BizException(EngineeringErrorCodes.ECN_NOT_EDITABLE);
        List<EcnLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(EngineeringErrorCodes.ECN_NO_LINES);
        Set<Long> oldIds = lines.stream().map(EcnLineDO::getOldComponentId).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> parentIds = bomMapper.selectBatchIds(lines.stream().map(EcnLineDO::getBomId).collect(Collectors.toSet())).stream()
                .map(BomDO::getMaterialId).collect(Collectors.toCollection(LinkedHashSet::new));

        List<EcnImpact> found = new ArrayList<>();
        if (!oldIds.isEmpty()) {
            Map<Long, StockSummary> stock = inventoryQueryApi.getStockSummary(oldIds);
            for (Long mid : oldIds) {
                StockSummary s = stock.get(mid);
                if (s != null && s.onHandQty() != null && s.onHandQty().signum() > 0) found.add(new EcnImpact(mid, "STOCK", null, s.onHandQty()));
            }
        }
        for (EcnImpactProvider p : impactProviders) {
            for (EcnImpact i : p.impacts(oldIds, parentIds)) {
                if (HANDLINGS.containsKey(i.impactType()) && !"STOCK".equals(i.impactType())) found.add(i);
            }
        }
        Map<String, EcnImpactDO> previous = impactMapper.selectByParent(id).stream().collect(Collectors.toMap(EcnService::impactKey, x -> x, (a, b) -> a));
        impactMapper.deleteByParent(id);
        List<EcnImpactDO> saved = new ArrayList<>();
        for (EcnImpact i : found) {
            EcnImpactDO x = new EcnImpactDO();
            x.setEcnId(id);
            x.setMaterialId(i.materialId());
            x.setImpactType(i.impactType());
            x.setDocNo(i.docNo());
            x.setQty(i.qty());
            EcnImpactDO prev = previous.get(impactKey(x));
            x.setHandling(prev != null ? prev.getHandling() : "SALES".equals(i.impactType()) ? "NO_ACTION" : null);
            x.setHandlingRemark(prev == null ? null : prev.getHandlingRemark());
            impactMapper.insert(x);
            saved.add(x);
        }

        boolean keyPart = involvesKeyPart(lines);
        Map<String, Long> assignees = new HashMap<>();
        taskMapper.selectByParent(id).forEach(t -> assignees.putIfAbsent(t.getDeptRole(), t.getAssigneeId()));
        taskMapper.deleteByParent(id);
        Map<Long, MaterialDO> ms = support.materials(saved.stream().map(EcnImpactDO::getMaterialId).toList());
        addTask(id, "WAREHOUSE", assignees, saved, "STOCK", ms, "处理库存");
        addTask(id, "PURCHASE", assignees, saved, "PURCHASE", ms, "处理在途采购");
        addTask(id, "PRODUCTION", assignees, saved, "WIP", ms, "处理在制生产订单");
        addTask(id, "PMC", assignees, saved, "SALES", ms, "确认受影响的销售订单交期");
        insertTask(id, "QUALITY", assignees.get("QUALITY"), "确认检验标准");
        if (keyPart) {
            Long certUser = assignees.containsKey("CERT") ? assignees.get("CERT")
                    : support.usersWithPermission("eng:cert:create").stream().findFirst().orElse(null);
            insertTask(id, "CERT", certUser, "认证评估：" + KEY_PART_WARNING);
        }
        e.setAnalyzed(true);
        e.setKeyPart(keyPart);
        mapper.updateByIdOrFail(e);
        support.log(BIZ_TYPE, id, e.getDocNo(), "ANALYZE", "分析影响", e.getStatus().name(), e.getStatus().name(), "影响 " + saved.size() + " 项");
    }

    private void addTask(Long ecnId, String role, Map<String, Long> assignees, List<EcnImpactDO> impacts, String type, Map<Long, MaterialDO> ms,
                         String title) {
        List<EcnImpactDO> list = impacts.stream().filter(i -> type.equals(i.getImpactType())).toList();
        if (list.isEmpty()) return;
        String detail = list.stream().map(i -> {
            MaterialDO m = ms.get(i.getMaterialId());
            String code = m == null ? String.valueOf(i.getMaterialId()) : m.getCode();
            return (i.getDocNo() == null ? "" : i.getDocNo() + " ") + code + " " + i.getQty().stripTrailingZeros().toPlainString();
        }).collect(Collectors.joining("；"));
        insertTask(ecnId, role, assignees.get(role), title + "：" + detail);
    }

    private static String impactKey(EcnImpactDO x) {
        return x.getImpactType() + "|" + x.getMaterialId() + "|" + Objects.toString(x.getDocNo(), "");
    }

    /** R08：原子件或新子件为关键件（BOM 行 is_key），或已关联认证 */
    private boolean involvesKeyPart(List<EcnLineDO> lines) {
        Set<Long> materials = new HashSet<>();
        for (EcnLineDO l : lines) {
            if (l.getOldComponentId() != null) {
                materials.add(l.getOldComponentId());
                boolean key = bomLineMapper.selectByBom(l.getBomId()).stream()
                        .anyMatch(b -> b.getComponentId().equals(l.getOldComponentId()) && Boolean.TRUE.equals(b.getIsKey()));
                if (key) return true;
            }
            if (l.getNewComponentId() != null) materials.add(l.getNewComponentId());
        }
        return !materials.isEmpty() && certMaterialMapper.selectCount(new LambdaQueryWrapper<CertificationMaterialDO>()
                .in(CertificationMaterialDO::getMaterialId, materials)) > 0;
    }

    // ==================== 提交与审批 ====================

    /** 提交（R01～R04、R08）：发起审批 ENG_ECN；未配置审批流时直接审核 */
    @Transactional(rollbackFor = Exception.class)
    public SubmitResult submit(Long id) {
        EcnDO e = getOrThrow(id);
        requireStatus(e, "提交", DocStatus.DRAFT);
        if ("DATE".equals(e.getEffectiveMode()) && (e.getEffectiveDate() == null || e.getEffectiveDate().isBefore(LocalDate.now()))) {
            throw new BizException(EngineeringErrorCodes.ECN_EFFECTIVE_DATE);
        }
        List<EcnLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(EngineeringErrorCodes.ECN_NO_LINES);
        Map<Long, BomDO> boms = checkBomsDefault(lines, EngineeringErrorCodes.ECN_BOM_NOT_DEFAULT);
        checkResult(lines, boms);
        if (!Boolean.TRUE.equals(e.getAnalyzed()) || impactMapper.selectByParent(id).stream().anyMatch(i -> !StringUtils.hasText(i.getHandling()))) {
            throw new BizException(EngineeringErrorCodes.ECN_IMPACT_REQUIRED);
        }
        checkLocked(e, boms.values());
        boolean keyPart = involvesKeyPart(lines);
        if (keyPart && taskMapper.selectByParent(id).stream().noneMatch(t -> "CERT".equals(t.getDeptRole()))) {
            insertTask(id, "CERT", support.usersWithPermission("eng:cert:create").stream().findFirst().orElse(null), "认证评估：" + KEY_PART_WARNING);
        }
        e.setKeyPart(keyPart);
        fire(e, DocAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("ecnType", e.getEcnType());
        vars.put("urgency", e.getUrgency());
        vars.put("keyPart", keyPart);
        StartResult r = workflowApi.start(BIZ_TYPE, e.getId(), e.getDocNo(), "ECN " + e.getDocNo() + " " + e.getTitle(), vars, Map.of(),
                support.currentUser());
        if (!r.isStarted()) approve(e);
        return new SubmitResult(e.getStatus().name(), keyPart ? KEY_PART_WARNING : null);
    }

    /** R01 / R05：每行 BOM 必须仍是当前默认且已审核的版本 */
    private Map<Long, BomDO> checkBomsDefault(List<EcnLineDO> lines, com.erp.common.exception.ErrorCode code) {
        Map<Long, BomDO> boms = new LinkedHashMap<>();
        for (EcnLineDO l : lines) {
            BomDO b = boms.computeIfAbsent(l.getBomId(), bomMapper::selectById);
            if (b == null) throw BizException.of(code, String.valueOf(l.getBomId()));
            if (b.getStatus() != DocStatus.APPROVED || !Boolean.TRUE.equals(b.getIsDefault())) throw BizException.of(code, b.getDocNo());
        }
        return boms;
    }

    /** R02：同一 BOM 同一子件只能有一个动作；变更后不能出现重复子件或循环引用 */
    private void checkResult(List<EcnLineDO> lines, Map<Long, BomDO> boms) {
        Map<Long, MaterialDO> ms = support.materials(lines.stream()
                .flatMap(l -> Stream.of(l.getOldComponentId(), l.getNewComponentId())).filter(Objects::nonNull).toList());
        Map<Long, List<EcnLineDO>> byBom = lines.stream().collect(Collectors.groupingBy(EcnLineDO::getBomId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Long, List<EcnLineDO>> en : byBom.entrySet()) {
            BomDO bom = boms.get(en.getKey());
            Set<Long> touched = new HashSet<>();
            for (EcnLineDO l : en.getValue()) {
                Long key = "ADD".equals(l.getAction()) ? l.getNewComponentId() : l.getOldComponentId();
                if (!touched.add(key)) throw BizException.of(EngineeringErrorCodes.ECN_COMPONENT_MULTI, bom.getDocNo(), code(ms, key));
            }
            List<Long> result = applyPreview(bomLineMapper.selectByBom(bom.getId()), en.getValue());
            Set<Long> seen = new HashSet<>();
            for (Long c : result) {
                if (!seen.add(c)) throw BizException.of(EngineeringErrorCodes.ECN_RESULT_DUPLICATE, bom.getDocNo(), code(ms, c));
            }
            Set<Long> added = en.getValue().stream().map(EcnLineDO::getNewComponentId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!added.isEmpty()) {
                List<Long> cycle = bomQueryService.findCycle(bom.getMaterialId(), added, bom.getId());
                if (!cycle.isEmpty()) {
                    Map<Long, MaterialDO> cms = support.materials(cycle);
                    throw BizException.of(EngineeringErrorCodes.BOM_CYCLE,
                            cycle.stream().map(c -> code(cms, c)).collect(Collectors.joining(" → ")));
                }
            }
        }
    }

    private static String code(Map<Long, MaterialDO> ms, Long id) {
        MaterialDO m = ms.get(id);
        return m == null ? String.valueOf(id) : m.getCode();
    }

    /** 变更后子件列表（用于重复检查） */
    static List<Long> applyPreview(List<BomLineDO> bomLines, List<EcnLineDO> changes) {
        List<Long> out = new ArrayList<>(bomLines.stream().map(BomLineDO::getComponentId).toList());
        for (EcnLineDO c : changes) {
            switch (c.getAction()) {
                case "REMOVE" -> out.remove(c.getOldComponentId());
                case "REPLACE" -> {
                    int i = out.indexOf(c.getOldComponentId());
                    if (i >= 0) out.set(i, c.getNewComponentId());
                }
                case "ADD" -> out.add(c.getNewComponentId());
                default -> {
                }
            }
        }
        return out;
    }

    /** R04：同一 BOM 不能同时在两张待审批/已审核的 ECN 中 */
    private void checkLocked(EcnDO e, java.util.Collection<BomDO> boms) {
        for (BomDO b : boms) {
            List<EcnLineDO> others = lineMapper.selectList(new LambdaQueryWrapper<EcnLineDO>().eq(EcnLineDO::getBomId, b.getId()).ne(EcnLineDO::getEcnId, e.getId()));
            if (others.isEmpty()) continue;
            List<EcnDO> locking = mapper.selectList(new LambdaQueryWrapper<EcnDO>()
                    .in(EcnDO::getId, others.stream().map(EcnLineDO::getEcnId).collect(Collectors.toSet())).in(EcnDO::getStatus, LOCKING));
            if (!locking.isEmpty()) throw BizException.of(EngineeringErrorCodes.ECN_BOM_LOCKED, b.getDocNo(), locking.get(0).getDocNo());
        }
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent ev) {
        if (!BIZ_TYPE.equals(ev.getBizType())) return;
        EcnDO e = getOrThrow(ev.getBizId());
        if (e.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (ev.getResult()) {
            case APPROVED -> approve(e);
            case WITHDRAWN -> fire(e, DocAction.WITHDRAW, null);
            default -> fire(e, DocAction.REJECT, ev.getComment());
        }
    }

    /** 审批通过（R05）：为每个 BOM 复制新版本并应用变更（直接已审核），发布 EcnApprovedEvent；立即生效或已到生效日期时执行生效 */
    private void approve(EcnDO e) {
        List<EcnLineDO> lines = lineMapper.selectByParent(e.getId());
        Map<Long, BomDO> boms = checkBomsDefault(lines, EngineeringErrorCodes.ECN_BOM_CHANGED);
        Map<Long, List<EcnLineDO>> byBom = lines.stream().collect(Collectors.groupingBy(EcnLineDO::getBomId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, MaterialDO> ms = support.materials(lines.stream().map(EcnLineDO::getNewComponentId).filter(Objects::nonNull).toList());
        List<EcnApprovedEvent.BomChange> changes = new ArrayList<>();
        for (Map.Entry<Long, List<EcnLineDO>> en : byBom.entrySet()) {
            BomDO old = boms.get(en.getKey());
            Long newId = bomService.newVersion(old.getId());
            applyChanges(newId, en.getValue(), ms);
            bomService.approveByEcn(newId, e.getId(), e.getDocNo());
            for (EcnLineDO l : en.getValue()) {
                l.setNewBomId(newId);
                lineMapper.updateByIdOrFail(l);
            }
            changes.add(new EcnApprovedEvent.BomChange(old.getMaterialId(), old.getId(), newId));
        }
        e.setApprovedAt(LocalDateTime.now());
        fire(e, DocAction.APPROVE, null);
        eventPublisher.publish(new EcnApprovedEvent(e.getId(), e.getDocNo(), changes));
        boolean due = "IMMEDIATE".equals(e.getEffectiveMode())
                || "DATE".equals(e.getEffectiveMode()) && e.getEffectiveDate() != null && !e.getEffectiveDate().isAfter(LocalDate.now());
        if (due) effect(e);
    }

    /** 在新版本的行上应用变更 */
    private void applyChanges(Long newBomId, List<EcnLineDO> changes, Map<Long, MaterialDO> ms) {
        List<BomLineDO> lines = bomLineMapper.selectByBom(newBomId);
        int maxNo = lines.stream().mapToInt(BomLineDO::getLineNo).max().orElse(0);
        for (EcnLineDO c : changes) {
            BomLineDO target = c.getOldComponentId() == null ? null
                    : lines.stream().filter(l -> l.getComponentId().equals(c.getOldComponentId())).findFirst().orElse(null);
            switch (c.getAction()) {
                case "REMOVE" -> {
                    if (target == null) continue;
                    substituteMapper.delete(new LambdaQueryWrapper<BomSubstituteDO>().eq(BomSubstituteDO::getBomLineId, target.getId()));
                    bomLineMapper.deleteById(target.getId());
                }
                case "REPLACE", "CHANGE_QTY" -> {
                    if (target == null) continue;
                    if ("REPLACE".equals(c.getAction())) {
                        target.setComponentId(c.getNewComponentId());
                        target.setUom(ms.get(c.getNewComponentId()).getBaseUom());
                        // 新子件本身作为替代料没有意义
                        substituteMapper.delete(new LambdaQueryWrapper<BomSubstituteDO>().eq(BomSubstituteDO::getBomLineId, target.getId())
                                .eq(BomSubstituteDO::getSubstituteId, c.getNewComponentId()));
                    }
                    target.setQtyPer(c.getNewQtyPer());
                    if (c.getNewScrapRate() != null) target.setScrapRate(c.getNewScrapRate());
                    if (c.getPositionNo() != null) target.setPositionNo(c.getPositionNo());
                    bomLineMapper.updateByIdOrFail(target);
                }
                case "ADD" -> {
                    BomLineDO l = new BomLineDO();
                    l.setBomId(newBomId);
                    l.setLineNo(++maxNo);
                    l.setComponentId(c.getNewComponentId());
                    l.setQtyPer(c.getNewQtyPer());
                    l.setUom(ms.get(c.getNewComponentId()).getBaseUom());
                    l.setScrapRate(c.getNewScrapRate() == null ? BigDecimal.ZERO : c.getNewScrapRate());
                    l.setPositionNo(c.getPositionNo());
                    l.setIssueMethod(com.erp.module.engineering.api.bom.IssueMethod.PICK);
                    l.setIsKey(false);
                    l.setRemark(c.getRemark());
                    bomLineMapper.insert(l);
                }
                default -> {
                }
            }
        }
    }

    // ==================== 生效 / 执行 / 关闭 ====================

    /** 手工生效（用完切换或提前生效） */
    @Transactional(rollbackFor = Exception.class)
    public void effect(Long id) {
        EcnDO e = getOrThrow(id);
        requireStatus(e, "生效", DocStatus.APPROVED);
        effect(e);
    }

    /** 生效：新 BOM 版本设为默认，发布 EcnEffectiveEvent，给执行任务负责人生成待办 */
    private void effect(EcnDO e) {
        List<EcnLineDO> lines = lineMapper.selectByParent(e.getId());
        Map<Long, Long> newByOld = new LinkedHashMap<>();
        lines.forEach(l -> newByOld.putIfAbsent(l.getBomId(), l.getNewBomId()));
        List<EcnApprovedEvent.BomChange> changes = new ArrayList<>();
        for (Map.Entry<Long, Long> en : newByOld.entrySet()) {
            if (en.getValue() == null) continue;
            BomDO nb = bomMapper.selectById(en.getValue());
            bomService.setDefault(nb.getId());
            changes.add(new EcnApprovedEvent.BomChange(nb.getMaterialId(), en.getKey(), nb.getId()));
        }
        e.setEffectedAt(LocalDateTime.now());
        fire(e, DocAction.START, null);
        List<String> updateWip = impactMapper.selectByParent(e.getId()).stream()
                .filter(i -> "WIP".equals(i.getImpactType()) && "UPDATE_WIP".equals(i.getHandling()) && i.getDocNo() != null)
                .map(EcnImpactDO::getDocNo).distinct().toList();
        eventPublisher.publish(new EcnEffectiveEvent(e.getId(), e.getDocNo(), changes, updateWip));
        for (EcnTaskDO t : taskMapper.selectByParent(e.getId())) {
            Long user = t.getAssigneeId() != null ? t.getAssigneeId() : e.getOwnerId();
            if (user == null || !"PENDING".equals(t.getTaskStatus())) continue;
            notifyApi.todo(new TodoCreatedEvent(todoKey(t), List.of(user), TodoCreatedEvent.Category.TASK, BIZ_TYPE, e.getId(), e.getDocNo(),
                    "ECN " + e.getDocNo() + " 执行确认：" + abbreviate(t.getContent()), "/engineering/ecn/" + e.getId(),
                    "URGENT".equals(e.getUrgency()) ? TodoCreatedEvent.Priority.HIGH : TodoCreatedEvent.Priority.NORMAL, null));
        }
    }

    private static String todoKey(EcnTaskDO t) {
        return "ENG_ECN_TASK:" + t.getId();
    }

    private static String abbreviate(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    /** R06：每天 00:10 处理生效日期已到的 ECN */
    @ErpJob(code = "ENG_ECN_EFFECT", name = "ECN 定时生效", cron = "0 10 0 * * ?")
    public String effectDue() {
        List<EcnDO> list = mapper.selectList(new LambdaQueryWrapper<EcnDO>().eq(EcnDO::getStatus, DocStatus.APPROVED)
                .eq(EcnDO::getEffectiveMode, "DATE").le(EcnDO::getEffectiveDate, LocalDate.now()));
        int ok = 0;
        List<String> failed = new ArrayList<>();
        for (EcnDO e : list) {
            try {
                tx.executeWithoutResult(st -> effect(e.getId()));
                ok++;
            } catch (RuntimeException ex) {
                failed.add(e.getDocNo() + "：" + ex.getMessage());
            }
        }
        return "生效 " + ok + " 张" + (failed.isEmpty() ? "" : "；失败 " + String.join("，", failed));
    }

    /** 执行确认完成：任务负责人（未指定负责人时为发起人） */
    @Transactional(rollbackFor = Exception.class)
    public void taskDone(Long id, Long taskId, String remark) {
        EcnDO e = getOrThrow(id);
        requireStatus(e, "确认完成", DocStatus.IN_PROGRESS);
        EcnTaskDO t = taskMapper.selectById(taskId);
        if (t == null || !t.getEcnId().equals(id)) throw new BizException(GlobalErrorCodes.DATA_NOT_EXISTS);
        Long me = support.currentUser();
        Long owner = t.getAssigneeId() != null ? t.getAssigneeId() : e.getOwnerId();
        if (me == null || !me.equals(owner)) throw new BizException(EngineeringErrorCodes.ECN_TASK_NOT_MINE);
        if ("DONE".equals(t.getTaskStatus())) return;
        t.setTaskStatus("DONE");
        t.setDoneRemark(EngSupport.trim(remark));
        t.setDoneBy(me);
        t.setDoneAt(LocalDateTime.now());
        taskMapper.updateByIdOrFail(t);
        notifyApi.done(new TodoDoneEvent(todoKey(t), null, TodoDoneEvent.Result.DONE));
        support.log(BIZ_TYPE, id, e.getDocNo(), "TASK_DONE", "执行确认", e.getStatus().name(), e.getStatus().name(), t.getContent());
    }

    /** 关闭（R07）：所有执行任务完成 */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        EcnDO e = getOrThrow(id);
        requireStatus(e, "关闭", DocStatus.IN_PROGRESS);
        long pending = taskMapper.selectByParent(id).stream().filter(t -> !"DONE".equals(t.getTaskStatus())).count();
        if (pending > 0) throw BizException.of(EngineeringErrorCodes.ECN_TASKS_UNDONE, pending);
        e.setClosedAt(LocalDateTime.now());
        fire(e, DocAction.COMPLETE, null);
    }

    private void fire(EcnDO e, DocAction action, String reason) {
        DocStatus from = e.getStatus();
        if (!DocStateMachines.STANDARD.canFire(from, action)) throw BizException.of(EngineeringErrorCodes.ECN_STATUS, label(from));
        e.setStatus(DocStateMachines.STANDARD.fire(from, action));
        mapper.updateByIdOrFail(e);
        support.log(BIZ_TYPE, e.getId(), e.getDocNo(), action.name(), actionLabel(action), from.name(), e.getStatus().name(), reason);
    }

    private static String actionLabel(DocAction a) {
        return switch (a) {
            case START -> "生效";
            case COMPLETE -> "关闭";
            default -> a.label();
        };
    }

    /** 界面状态名：已审核 / 已生效 / 已关闭 */
    static String label(DocStatus s) {
        return switch (s) {
            case IN_PROGRESS -> "已生效";
            case COMPLETED -> "已关闭";
            default -> s.label();
        };
    }

    private static void requireStatus(EcnDO e, String action, DocStatus expected) {
        if (e.getStatus() != expected) throw BizException.of(EngineeringErrorCodes.ECN_STATUS, label(e.getStatus()));
    }

    public EcnDO getOrThrow(Long id) {
        EcnDO e = id == null ? null : mapper.selectById(id);
        if (e == null) throw new BizException(EngineeringErrorCodes.ECN_NOT_EXISTS);
        return e;
    }

    // ==================== 查询 ====================

    public PageResult<EcnRow> page(EcnQuery q) {
        LambdaQueryWrapper<EcnDO> w = new LambdaQueryWrapper<EcnDO>()
                .likeRight(StringUtils.hasText(q.getDocNo()), EcnDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .like(StringUtils.hasText(q.getTitle()), EcnDO::getTitle, q.getTitle() == null ? null : q.getTitle().trim())
                .eq(StringUtils.hasText(q.getEcnType()), EcnDO::getEcnType, q.getEcnType())
                .ge(q.getDateFrom() != null, EcnDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, EcnDO::getDocDate, q.getDateTo());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(EcnDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        } else {
            w.notIn(EcnDO::getStatus, DocStatus.COMPLETED, DocStatus.VOIDED);
        }
        if (q.getMaterialId() != null) {
            long m = q.getMaterialId();
            w.inSql(EcnDO::getId, "SELECT l.ecn_id FROM eng_ecn_line l LEFT JOIN eng_bom b ON b.id = l.bom_id WHERE l.deleted = 0 AND (l.old_component_id = "
                    + m + " OR l.new_component_id = " + m + " OR b.material_id = " + m + ")");
        }
        w.orderByDesc(EcnDO::getId);
        PageResult<EcnDO> page = mapper.selectPage(q, w);
        Map<Long, UserDTO> users = support.users(page.list().stream().map(EcnDO::getCreatedBy).toList());
        return new PageResult<>(page.list().stream().map(e -> new EcnRow(e.getId(), e.getDocNo(), e.getTitle(), e.getEcnType(), e.getReasonType(),
                e.getUrgency(), e.getEffectiveMode(), e.getEffectiveDate(), e.getStatus().name(), EngSupport.name(users, e.getCreatedBy()),
                e.getDocDate(), e.getCreatedAt())).toList(), page.total());
    }

    public List<EcnRow> listForExport(EcnQuery q, int limit) {
        q.setPageNo(1);
        q.setPageSize(Math.min(limit, 100_000));
        return page(q).list();
    }

    public EcnDetail detail(Long id) {
        EcnDO e = getOrThrow(id);
        List<EcnLineDO> lines = lineMapper.selectByParent(id);
        List<EcnImpactDO> impacts = impactMapper.selectByParent(id);
        List<EcnTaskDO> tasks = taskMapper.selectByParent(id);
        Set<Long> bomIds = new HashSet<>();
        lines.forEach(l -> {
            bomIds.add(l.getBomId());
            if (l.getNewBomId() != null) bomIds.add(l.getNewBomId());
        });
        Map<Long, BomDO> boms = bomIds.isEmpty() ? Map.of()
                : bomMapper.selectBatchIds(bomIds).stream().collect(Collectors.toMap(BomDO::getId, b -> b));
        Set<Long> mids = new HashSet<>();
        boms.values().forEach(b -> mids.add(b.getMaterialId()));
        lines.forEach(l -> {
            mids.add(l.getOldComponentId());
            mids.add(l.getNewComponentId());
        });
        impacts.forEach(i -> mids.add(i.getMaterialId()));
        Map<Long, MaterialDO> ms = support.materials(mids);
        Map<Long, UserDTO> users = support.users(Stream.concat(Stream.of(e.getCreatedBy()),
                tasks.stream().flatMap(t -> Stream.of(t.getAssigneeId(), t.getDoneBy()))).toList());
        Map<Long, String> customers = support.customers(e.getCustomerId() == null ? List.of() : List.of(e.getCustomerId()));
        Long me = support.currentUser();

        List<LineResp> lineResps = lines.stream().map(l -> {
            BomDO b = boms.get(l.getBomId());
            MaterialDO p = b == null ? null : ms.get(b.getMaterialId());
            MaterialDO o = ms.get(l.getOldComponentId());
            MaterialDO n = ms.get(l.getNewComponentId());
            BomDO nb = l.getNewBomId() == null ? null : boms.get(l.getNewBomId());
            String uom = n != null ? n.getBaseUom() : o == null ? null : o.getBaseUom();
            return new LineResp(l.getId(), l.getLineNo(), l.getBomId(), b == null ? null : b.getDocNo(), p == null ? null : p.getId(),
                    p == null ? null : p.getCode(), p == null ? null : p.getName(), l.getAction(), l.getOldComponentId(), o == null ? null : o.getCode(),
                    o == null ? null : o.getName(), l.getNewComponentId(), n == null ? null : n.getCode(), n == null ? null : n.getName(), uom,
                    l.getOldQtyPer(), l.getNewQtyPer(), l.getOldScrapRate(), l.getNewScrapRate(), l.getPositionNo(), l.getNewBomId(),
                    nb == null ? null : nb.getDocNo(), l.getRemark());
        }).toList();
        List<ImpactResp> impactResps = impacts.stream().map(i -> {
            MaterialDO m = ms.get(i.getMaterialId());
            return new ImpactResp(i.getId(), i.getMaterialId(), m == null ? null : m.getCode(), m == null ? null : m.getName(),
                    m == null ? null : m.getBaseUom(), i.getImpactType(), i.getDocNo(), i.getQty(), i.getHandling(), i.getHandlingRemark());
        }).toList();
        List<TaskResp> taskResps = tasks.stream().map(t -> {
            Long owner = t.getAssigneeId() != null ? t.getAssigneeId() : e.getOwnerId();
            return new TaskResp(t.getId(), t.getDeptRole(), t.getAssigneeId(), EngSupport.name(users, t.getAssigneeId()), t.getContent(),
                    t.getTaskStatus(), t.getDoneRemark(), EngSupport.name(users, t.getDoneBy()), t.getDoneAt(),
                    me != null && me.equals(owner) && "PENDING".equals(t.getTaskStatus()));
        }).toList();
        int pending = (int) tasks.stream().filter(t -> !"DONE".equals(t.getTaskStatus())).count();
        return new EcnDetail(e.getId(), e.getDocNo(), e.getDocDate(), e.getTitle(), e.getEcnType(), e.getReasonType(), e.getReason(), e.getUrgency(),
                e.getEffectiveMode(), e.getEffectiveDate(), e.getCustomerId(), customers.get(e.getCustomerId()), Boolean.TRUE.equals(e.getAnalyzed()),
                Boolean.TRUE.equals(e.getKeyPart()), e.getStatus().name(), e.getApprovedAt(), e.getEffectedAt(), e.getClosedAt(),
                EngSupport.name(users, e.getCreatedBy()), e.getCreatedAt(), e.getVersion(), lineResps, impactResps, taskResps, pending);
    }

    // ==================== 打印 ====================

    static final Map<String, String> ACTION_NAMES = Map.of("ADD", "新增", "REMOVE", "删除", "REPLACE", "替换", "CHANGE_QTY", "修改用量");
    static final Map<String, String> MODE_NAMES = Map.of("IMMEDIATE", "立即生效", "DATE", "指定日期", "USE_UP", "旧料用完后切换");
    static final Map<String, String> ROLE_NAMES = Map.of("PURCHASE", "采购", "WAREHOUSE", "仓库", "PRODUCTION", "生产", "QUALITY", "品质", "PMC", "PMC",
            "CERT", "认证");

    public Map<String, Object> printData(Long id) {
        EcnDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("docDate", d.docDate());
        data.put("status", label(DocStatus.valueOf(d.status())));
        data.put("title", d.title());
        data.put("ecnType", Objects.toString(dictApi.label("eng_ecn_type", d.ecnType()), d.ecnType()));
        data.put("reasonType", Objects.toString(dictApi.label("eng_ecn_reason", d.reasonType()), d.reasonType()));
        data.put("reason", d.reason());
        data.put("urgency", "URGENT".equals(d.urgency()) ? "紧急" : "普通");
        data.put("effectiveMode", MODE_NAMES.get(d.effectiveMode()));
        data.put("effectiveDate", d.effectiveDate());
        data.put("createdByName", Objects.toString(d.createdByName(), ""));
        data.put("lines", d.lines().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.lineNo());
            m.put("bomNo", l.bomNo());
            m.put("parentCode", l.parentCode());
            m.put("action", ACTION_NAMES.get(l.action()));
            m.put("oldCode", Objects.toString(l.oldCode(), ""));
            m.put("oldName", Objects.toString(l.oldName(), ""));
            m.put("oldQtyPer", l.oldQtyPer());
            m.put("newCode", Objects.toString(l.newCode(), ""));
            m.put("newName", Objects.toString(l.newName(), ""));
            m.put("newQtyPer", l.newQtyPer());
            m.put("newBomNo", Objects.toString(l.newBomNo(), ""));
            return m;
        }).toList());
        data.put("tasks", d.tasks().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deptRole", ROLE_NAMES.get(t.deptRole()));
            m.put("content", t.content());
            m.put("assigneeName", Objects.toString(t.assigneeName(), ""));
            m.put("status", "DONE".equals(t.taskStatus()) ? "已完成" : "待处理");
            return m;
        }).toList());
        return data;
    }
}
