package com.erp.module.engineering.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocAction;
import com.erp.common.enums.DocStatus;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.StateMachine;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.routing.RoutingApi;
import com.erp.module.engineering.api.routing.RoutingDTO;
import com.erp.module.engineering.api.routing.RoutingReferenceChecker;
import com.erp.module.engineering.controller.vo.RoutingVOs.RoutingDetail;
import com.erp.module.engineering.controller.vo.RoutingVOs.RoutingQuery;
import com.erp.module.engineering.controller.vo.RoutingVOs.RoutingRow;
import com.erp.module.engineering.controller.vo.RoutingVOs.RoutingSave;
import com.erp.module.engineering.controller.vo.RoutingVOs.SaveResult;
import com.erp.module.engineering.controller.vo.RoutingVOs.StepResp;
import com.erp.module.engineering.controller.vo.RoutingVOs.StepSave;
import com.erp.module.engineering.controller.vo.RoutingVOs.WcSummary;
import com.erp.module.engineering.dal.dataobject.BomDO;
import com.erp.module.engineering.dal.dataobject.BomLineDO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.dataobject.RoutingDO;
import com.erp.module.engineering.dal.dataobject.RoutingStepDO;
import com.erp.module.engineering.dal.dataobject.WorkCenterDO;
import com.erp.module.engineering.dal.mapper.BomLineMapper;
import com.erp.module.engineering.dal.mapper.BomMapper;
import com.erp.module.engineering.dal.mapper.RoutingMapper;
import com.erp.module.engineering.dal.mapper.RoutingStepMapper;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工艺路线（需求 05-04）：草稿 →（eng:routing:approve 直接审核，无审批流）→ 已审核 → 停用（CLOSED）；
 * 已审核非默认版本可反审核、设为默认；首个审核版本自动为默认。实现 RoutingApi。
 */
@Service
public class RoutingService implements RoutingApi {

    public static final String BIZ_TYPE = "ENG_ROUTING";
    static final Set<MaterialType> TYPES = Set.of(MaterialType.SEMI_FINISHED, MaterialType.FINISHED);

    static final StateMachine<DocStatus, DocAction> MACHINE = StateMachine.builder(DocStatus.class, DocAction.class)
            .transition(DocStatus.DRAFT, DocAction.APPROVE, DocStatus.APPROVED)
            .transition(DocStatus.APPROVED, DocAction.UNAPPROVE, DocStatus.DRAFT)
            .transition(DocStatus.APPROVED, DocAction.CLOSE, DocStatus.CLOSED)
            .build();

    private final RoutingMapper mapper;
    private final RoutingStepMapper stepMapper;
    private final BomMapper bomMapper;
    private final BomLineMapper bomLineMapper;
    private final WorkCenterService workCenterService;
    private final EngSupport support;
    private final List<RoutingReferenceChecker> referenceCheckers;

    public RoutingService(RoutingMapper mapper, RoutingStepMapper stepMapper, BomMapper bomMapper, BomLineMapper bomLineMapper,
                          WorkCenterService workCenterService, EngSupport support, List<RoutingReferenceChecker> referenceCheckers) {
        this.mapper = mapper;
        this.stepMapper = stepMapper;
        this.bomMapper = bomMapper;
        this.bomLineMapper = bomLineMapper;
        this.workCenterService = workCenterService;
        this.support = support;
        this.referenceCheckers = referenceCheckers;
    }

    // ==================== 保存 ====================

    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(RoutingSave req) {
        MaterialDO m = support.material(req.materialId());
        if (!TYPES.contains(m.getMaterialType())) throw new BizException(EngineeringErrorCodes.ROUTING_MATERIAL_TYPE);
        checkSteps(req.steps());
        int version = maxVersion(m.getId()) + 1;
        RoutingDO r = new RoutingDO();
        r.setDocNo(m.getCode() + "-R" + version);
        r.setDocDate(LocalDate.now());
        r.setStatus(DocStatus.DRAFT);
        r.setOwnerId(support.currentUser());
        r.setMaterialId(m.getId());
        r.setRoutingVersion(version);
        r.setIsDefault(false);
        r.setDescription(EngSupport.trim(req.description()));
        r.setRemark(EngSupport.trim(req.remark()));
        mapper.insert(r);
        saveSteps(r, req.steps());
        return new SaveResult(r.getId(), bomWarnings(r));
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult update(Long id, RoutingSave req) {
        RoutingDO r = getOrThrow(id);
        if (r.getStatus() != DocStatus.DRAFT) throw new BizException(EngineeringErrorCodes.ROUTING_NOT_EDITABLE);
        checkSteps(req.steps());
        if (req.rowVersion() != null) r.setVersion(req.rowVersion());
        r.setDescription(EngSupport.trim(req.description()));
        r.setRemark(EngSupport.trim(req.remark()));
        mapper.updateByIdOrFail(r);
        saveSteps(r, req.steps());
        return new SaveResult(r.getId(), bomWarnings(r));
    }

    private int maxVersion(Long materialId) {
        return mapper.selectList(new LambdaQueryWrapper<RoutingDO>().eq(RoutingDO::getMaterialId, materialId)).stream()
                .mapToInt(RoutingDO::getRoutingVersion).max().orElse(0);
    }

    /** R01 工序号唯一且为正整数；R03 委外工序须用委外工作中心；标准工时 > 0 */
    private void checkSteps(List<StepSave> steps) {
        if (steps == null) return;
        Map<Long, WorkCenterDO> wcs = workCenterService.byIds(steps.stream().map(StepSave::workCenterId).toList());
        Set<Integer> seqs = new HashSet<>();
        int n = 0;
        for (StepSave s : steps) {
            n++;
            if (!seqs.add(s.seq())) throw BizException.of(EngineeringErrorCodes.ROUTING_SEQ_DUPLICATE, s.seq());
            WorkCenterDO wc = wcs.get(s.workCenterId());
            if (wc == null) throw new BizException(EngineeringErrorCodes.WORK_CENTER_NOT_EXISTS);
            if (Boolean.TRUE.equals(s.isOutsourced()) && !"OUTSOURCE".equals(wc.getWcType())) throw new BizException(EngineeringErrorCodes.ROUTING_OUTSOURCE_WC);
            if (s.runSeconds() == null || s.runSeconds().signum() <= 0) throw BizException.of(EngineeringErrorCodes.ROUTING_RUN_SECONDS, n);
        }
    }

    private void saveSteps(RoutingDO r, List<StepSave> steps) {
        stepMapper.deleteByParent(r.getId());
        List<StepSave> list = steps == null ? List.of() : steps.stream().sorted((a, b) -> Integer.compare(a.seq(), b.seq())).toList();
        BigDecimal setup = BigDecimal.ZERO;
        BigDecimal run = BigDecimal.ZERO;
        for (StepSave s : list) {
            RoutingStepDO x = new RoutingStepDO();
            x.setRoutingId(r.getId());
            x.setSeq(s.seq());
            x.setOperation(s.operation());
            x.setWorkCenterId(s.workCenterId());
            x.setSetupMinutes(s.setupMinutes() == null ? BigDecimal.ZERO : s.setupMinutes());
            x.setRunSeconds(s.runSeconds());
            x.setIsReportPoint(s.isReportPoint() == null || s.isReportPoint());
            x.setIsInspectionPoint(Boolean.TRUE.equals(s.isInspectionPoint()));
            x.setIsOutsourced(Boolean.TRUE.equals(s.isOutsourced()));
            x.setRemark(EngSupport.trim(s.remark()));
            stepMapper.insert(x);
            setup = setup.add(x.getSetupMinutes());
            run = run.add(x.getRunSeconds());
        }
        r.setStepCount(list.size());
        r.setTotalSetupMinutes(setup);
        r.setTotalRunSeconds(run);
        mapper.updateByIdOrFail(r);
    }

    /** R06（警告）：父件默认 BOM 的行引用了本路线中不存在的工序号 */
    private List<String> bomWarnings(RoutingDO r) {
        BomDO bom = bomMapper.selectDefault(r.getMaterialId());
        if (bom == null) return List.of();
        Set<Integer> seqs = stepMapper.selectByParent(r.getId()).stream().map(RoutingStepDO::getSeq).collect(Collectors.toSet());
        return bomLineMapper.selectByBom(bom.getId()).stream().map(BomLineDO::getOperationSeq)
                .filter(seq -> seq != null && !seqs.contains(seq)).distinct().sorted()
                .map(seq -> "BOM 中引用了工序 " + seq + "，新版本中不存在").toList();
    }

    // ==================== 状态 ====================

    /** 审核（R02）：至少一道工序；最后一道为报工点；工作中心均为启用；首个审核版本自动为默认 */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        RoutingDO r = getOrThrow(id);
        List<RoutingStepDO> steps = stepMapper.selectByParent(id);
        if (steps.isEmpty()) throw new BizException(EngineeringErrorCodes.ROUTING_NO_STEPS);
        if (!Boolean.TRUE.equals(steps.get(steps.size() - 1).getIsReportPoint())) throw new BizException(EngineeringErrorCodes.ROUTING_LAST_REPORT_POINT);
        Map<Long, WorkCenterDO> wcs = workCenterService.byIds(steps.stream().map(RoutingStepDO::getWorkCenterId).toList());
        for (RoutingStepDO s : steps) {
            WorkCenterDO wc = wcs.get(s.getWorkCenterId());
            if (wc == null || !EnableStatus.ENABLED.name().equals(wc.getStatus())) {
                throw BizException.of(EngineeringErrorCodes.WORK_CENTER_DISABLED, wc == null ? String.valueOf(s.getWorkCenterId()) : wc.getCode());
            }
        }
        fire(r, DocAction.APPROVE, null);
        if (selectDefault(r.getMaterialId()) == null) {
            r.setIsDefault(true);
            r.setEffectiveDate(LocalDate.now());
            mapper.updateByIdOrFail(r);
        }
    }

    /** 反审核（R05）：默认版本、被生产订单使用的版本不能反审核 */
    @Transactional(rollbackFor = Exception.class)
    public void unapprove(Long id, String reason) {
        RoutingDO r = getOrThrow(id);
        if (Boolean.TRUE.equals(r.getIsDefault())) throw new BizException(EngineeringErrorCodes.ROUTING_DEFAULT_UNAPPROVE);
        if (referenceCheckers.stream().anyMatch(c -> c.isRoutingUsed(id))) throw new BizException(EngineeringErrorCodes.ROUTING_USED_UNAPPROVE);
        fire(r, DocAction.UNAPPROVE, reason);
    }

    /** 设为默认（R04）：同一物料一个默认版本 */
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        RoutingDO r = getOrThrow(id);
        if (r.getStatus() != DocStatus.APPROVED) throw new BizException(EngineeringErrorCodes.ROUTING_SET_DEFAULT_STATUS);
        if (Boolean.TRUE.equals(r.getIsDefault())) return;
        RoutingDO old = selectDefault(r.getMaterialId());
        if (old != null) {
            old.setIsDefault(false);
            mapper.updateByIdOrFail(old);
        }
        r.setIsDefault(true);
        r.setEffectiveDate(LocalDate.now());
        mapper.updateByIdOrFail(r);
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), "SET_DEFAULT", "设为默认", r.getStatus().name(), r.getStatus().name(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        RoutingDO r = getOrThrow(id);
        if (Boolean.TRUE.equals(r.getIsDefault())) throw new BizException(EngineeringErrorCodes.ROUTING_DEFAULT_DISABLE);
        fire(r, DocAction.CLOSE, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        RoutingDO r = getOrThrow(id);
        if (r.getStatus() != DocStatus.DRAFT) throw new BizException(EngineeringErrorCodes.ROUTING_NOT_EDITABLE);
        stepMapper.deleteByParent(id);
        mapper.deleteById(id);
    }

    /** 新建版本：复制为新草稿 */
    @Transactional(rollbackFor = Exception.class)
    public Long newVersion(Long id) {
        RoutingDO src = getOrThrow(id);
        if (src.getStatus() != DocStatus.APPROVED && src.getStatus() != DocStatus.CLOSED) {
            throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, src.getStatus().label(), "新建版本");
        }
        MaterialDO m = support.material(src.getMaterialId());
        int version = maxVersion(m.getId()) + 1;
        RoutingDO r = new RoutingDO();
        r.setDocNo(m.getCode() + "-R" + version);
        r.setDocDate(LocalDate.now());
        r.setStatus(DocStatus.DRAFT);
        r.setOwnerId(support.currentUser());
        r.setMaterialId(m.getId());
        r.setRoutingVersion(version);
        r.setIsDefault(false);
        r.setCopiedFromId(src.getId());
        r.setRemark(src.getRemark());
        r.setStepCount(src.getStepCount());
        r.setTotalSetupMinutes(src.getTotalSetupMinutes());
        r.setTotalRunSeconds(src.getTotalRunSeconds());
        mapper.insert(r);
        for (RoutingStepDO s : stepMapper.selectByParent(src.getId())) {
            s.setId(null);
            s.setVersion(null);
            s.setRoutingId(r.getId());
            stepMapper.insert(s);
        }
        return r.getId();
    }

    private void fire(RoutingDO r, DocAction action, String reason) {
        DocStatus old = r.getStatus();
        r.setStatus(MACHINE.fire(old, action));
        mapper.updateByIdOrFail(r);
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), action.name(), action == DocAction.CLOSE ? "停用" : action.label(), old.name(), r.getStatus().name(), reason);
    }

    private RoutingDO selectDefault(Long materialId) {
        return mapper.selectOne(new LambdaQueryWrapper<RoutingDO>().eq(RoutingDO::getMaterialId, materialId).eq(RoutingDO::getIsDefault, true)
                .eq(RoutingDO::getStatus, DocStatus.APPROVED).last("LIMIT 1"));
    }

    public RoutingDO getOrThrow(Long id) {
        RoutingDO r = id == null ? null : mapper.selectById(id);
        if (r == null) throw new BizException(EngineeringErrorCodes.ROUTING_NOT_EXISTS);
        return r;
    }

    // ==================== 查询 ====================

    public PageResult<RoutingRow> page(RoutingQuery q) {
        LambdaQueryWrapper<RoutingDO> w = new LambdaQueryWrapper<RoutingDO>()
                .eq(q.getMaterialId() != null, RoutingDO::getMaterialId, q.getMaterialId())
                .eq(Boolean.TRUE.equals(q.getDefaultOnly()), RoutingDO::getIsDefault, true);
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(RoutingDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        if (StringUtils.hasText(q.getKeyword())) w.likeRight(RoutingDO::getDocNo, q.getKeyword().trim().toUpperCase());
        if (q.getWorkCenterId() != null) {
            w.inSql(RoutingDO::getId, "SELECT routing_id FROM eng_routing_step WHERE deleted = 0 AND work_center_id = " + q.getWorkCenterId().longValue());
        }
        w.orderByDesc(RoutingDO::getUpdatedAt);
        PageResult<RoutingDO> page = mapper.selectPage(q, w);
        Map<Long, MaterialDO> ms = support.materials(page.list().stream().map(RoutingDO::getMaterialId).toList());
        Map<Long, UserDTO> users = support.users(page.list().stream().map(RoutingDO::getUpdatedBy).toList());
        return new PageResult<>(page.list().stream().map(r -> {
            MaterialDO m = ms.get(r.getMaterialId());
            return new RoutingRow(r.getId(), r.getDocNo(), r.getMaterialId(), m == null ? null : m.getCode(), m == null ? null : m.getName(),
                    m == null ? null : m.getSpec(), r.getRoutingVersion(), Boolean.TRUE.equals(r.getIsDefault()), r.getStepCount(), r.getTotalRunSeconds(),
                    r.getDescription(), r.getStatus().name(), EngSupport.name(users, r.getUpdatedBy()), r.getUpdatedAt());
        }).toList(), page.total());
    }

    public RoutingDetail detail(Long id) {
        RoutingDO r = getOrThrow(id);
        MaterialDO m = support.material(r.getMaterialId());
        List<RoutingStepDO> steps = stepMapper.selectByParent(id);
        Map<Long, WorkCenterDO> wcs = workCenterService.byIds(steps.stream().map(RoutingStepDO::getWorkCenterId).toList());
        Map<Long, UserDTO> users = support.users(List.of(nz(r.getCreatedBy()), nz(r.getUpdatedBy())));
        Map<Long, WcSummary> byWc = new LinkedHashMap<>();
        for (RoutingStepDO s : steps) {
            WorkCenterDO wc = wcs.get(s.getWorkCenterId());
            WcSummary old = byWc.get(s.getWorkCenterId());
            byWc.put(s.getWorkCenterId(), new WcSummary(s.getWorkCenterId(), wc == null ? null : wc.getName(),
                    (old == null ? BigDecimal.ZERO : old.setupMinutes()).add(s.getSetupMinutes()),
                    (old == null ? BigDecimal.ZERO : old.runSeconds()).add(s.getRunSeconds())));
        }
        RoutingDO from = r.getCopiedFromId() == null ? null : mapper.selectById(r.getCopiedFromId());
        return new RoutingDetail(r.getId(), r.getDocNo(), m.getId(), m.getCode(), m.getName(), m.getSpec(), m.getMaterialType().name(), r.getRoutingVersion(),
                Boolean.TRUE.equals(r.getIsDefault()), r.getDescription(), r.getRemark(), r.getStatus().name(), r.getCopiedFromId(),
                from == null ? null : from.getDocNo(), r.getTotalSetupMinutes(), r.getTotalRunSeconds(), new ArrayList<>(byWc.values()),
                EngSupport.name(users, r.getCreatedBy()), r.getCreatedAt(), EngSupport.name(users, r.getUpdatedBy()), r.getUpdatedAt(), r.getVersion(),
                steps.stream().map(s -> {
                    WorkCenterDO wc = wcs.get(s.getWorkCenterId());
                    return new StepResp(s.getId(), s.getSeq(), s.getOperation(), s.getWorkCenterId(), wc == null ? null : wc.getCode(),
                            wc == null ? null : wc.getName(), wc == null ? null : wc.getWcType(), s.getSetupMinutes(), s.getRunSeconds(),
                            Boolean.TRUE.equals(s.getIsReportPoint()), Boolean.TRUE.equals(s.getIsInspectionPoint()), Boolean.TRUE.equals(s.getIsOutsourced()),
                            s.getRemark());
                }).toList());
    }

    private static Long nz(Long v) {
        return v == null ? 0L : v;
    }

    // ==================== RoutingApi ====================

    @Override
    public Optional<RoutingDTO> getDefaultRouting(Long materialId) {
        return Optional.ofNullable(selectDefault(materialId)).map(this::toDto);
    }

    @Override
    public Optional<RoutingDTO> getRouting(Long routingId) {
        return Optional.ofNullable(routingId == null ? null : mapper.selectById(routingId)).map(this::toDto);
    }

    private RoutingDTO toDto(RoutingDO r) {
        return new RoutingDTO(r.getId(), r.getDocNo(), r.getMaterialId(), r.getRoutingVersion(), Boolean.TRUE.equals(r.getIsDefault()),
                stepMapper.selectByParent(r.getId()).stream().map(s -> new RoutingDTO.Step(s.getSeq(), s.getOperation(), s.getWorkCenterId(),
                        s.getSetupMinutes(), s.getRunSeconds(), Boolean.TRUE.equals(s.getIsReportPoint()), Boolean.TRUE.equals(s.getIsInspectionPoint()),
                        Boolean.TRUE.equals(s.getIsOutsourced()), s.getRemark())).toList());
    }
}
