package com.erp.module.engineering.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.routing.RoutingReferenceChecker;
import com.erp.module.engineering.api.routing.WorkCenterApi;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.engineering.controller.vo.RoutingVOs.WorkCenterQuery;
import com.erp.module.engineering.controller.vo.RoutingVOs.WorkCenterRow;
import com.erp.module.engineering.controller.vo.RoutingVOs.WorkCenterSave;
import com.erp.module.engineering.controller.vo.RoutingVOs.WorkCenterSimple;
import com.erp.module.engineering.dal.dataobject.RoutingStepDO;
import com.erp.module.engineering.dal.dataobject.WorkCenterDO;
import com.erp.module.engineering.dal.mapper.RoutingStepMapper;
import com.erp.module.engineering.dal.mapper.WorkCenterMapper;
import com.erp.module.system.api.org.OrgDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 工作中心（需求 05-04 3.1）；实现 WorkCenterApi */
@Service
public class WorkCenterService implements WorkCenterApi {

    public static final Set<String> TYPES = Set.of("LINE", "MACHINE", "MANUAL", "OUTSOURCE");
    static final String RATE_PERMISSION = "eng:work-center:rate";

    private final WorkCenterMapper mapper;
    private final RoutingStepMapper stepMapper;
    private final EngSupport support;
    private final List<RoutingReferenceChecker> referenceCheckers;

    public WorkCenterService(WorkCenterMapper mapper, RoutingStepMapper stepMapper, EngSupport support, List<RoutingReferenceChecker> referenceCheckers) {
        this.mapper = mapper;
        this.stepMapper = stepMapper;
        this.support = support;
        this.referenceCheckers = referenceCheckers;
    }

    /** 日可用产能 = 每班小时 × 班次 × 效率 */
    static BigDecimal capacity(WorkCenterDO w) {
        return w.getHoursPerShift().multiply(BigDecimal.valueOf(w.getShiftCount())).multiply(w.getEfficiencyPct())
                .setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    public PageResult<WorkCenterRow> page(WorkCenterQuery q) {
        LambdaQueryWrapper<WorkCenterDO> w = new LambdaQueryWrapper<WorkCenterDO>()
                .eq(q.getDeptId() != null, WorkCenterDO::getDeptId, q.getDeptId())
                .eq(StringUtils.hasText(q.getWcType()), WorkCenterDO::getWcType, q.getWcType())
                .eq(StringUtils.hasText(q.getStatus()), WorkCenterDO::getStatus, q.getStatus())
                .orderByAsc(WorkCenterDO::getCode);
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.likeRight(WorkCenterDO::getCode, k.toUpperCase()).or().like(WorkCenterDO::getName, k));
        }
        PageResult<WorkCenterDO> page = mapper.selectPage(q, w);
        Map<Long, OrgDTO> orgs = support.orgs(page.list().stream().map(WorkCenterDO::getDeptId).toList());
        boolean rate = EngSupport.hasPermission(RATE_PERMISSION);
        return new PageResult<>(page.list().stream().map(x -> new WorkCenterRow(x.getId(), x.getCode(), x.getName(), x.getDeptId(),
                orgs.containsKey(x.getDeptId()) ? orgs.get(x.getDeptId()).name() : null, x.getWcType(), x.getHoursPerShift(), x.getShiftCount(),
                x.getEfficiencyPct(), capacity(x), rate ? x.getLaborRate() : null, rate ? x.getOverheadRate() : null, rate, x.getStatus(),
                x.getRemark(), x.getVersion())).toList(), page.total());
    }

    public List<WorkCenterSimple> simple() {
        return mapper.selectList(new LambdaQueryWrapper<WorkCenterDO>().eq(WorkCenterDO::getStatus, EnableStatus.ENABLED.name())
                .orderByAsc(WorkCenterDO::getCode)).stream().map(x -> new WorkCenterSimple(x.getId(), x.getCode(), x.getName(), x.getWcType())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(WorkCenterSave req) {
        WorkCenterDO w = new WorkCenterDO();
        w.setStatus(EnableStatus.ENABLED.name());
        fill(w, req, null);
        mapper.insert(w);
        return w.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, WorkCenterSave req) {
        WorkCenterDO w = getOrThrow(id);
        if (req.version() != null) w.setVersion(req.version());
        fill(w, req, id);
        mapper.updateByIdOrFail(w);
    }

    private void fill(WorkCenterDO w, WorkCenterSave req, Long id) {
        String code = req.code().trim().toUpperCase();
        if (mapper.selectCount(new LambdaQueryWrapper<WorkCenterDO>().eq(WorkCenterDO::getCode, code).ne(id != null, WorkCenterDO::getId, id)) > 0) {
            throw BizException.of(EngineeringErrorCodes.WORK_CENTER_CODE_DUPLICATE, code);
        }
        if (!TYPES.contains(req.wcType())) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.BAD_REQUEST, "工作中心类型");
        if (!support.orgExists(req.deptId())) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.DATA_NOT_EXISTS, "车间");
        w.setCode(code);
        w.setName(req.name().trim());
        w.setDeptId(req.deptId());
        w.setWcType(req.wcType());
        w.setHoursPerShift(req.hoursPerShift());
        w.setShiftCount(req.shiftCount());
        w.setEfficiencyPct(req.efficiencyPct());
        // 没有费率权限的人修改时保留原值
        if (EngSupport.hasPermission(RATE_PERMISSION)) {
            w.setLaborRate(req.laborRate());
            w.setOverheadRate(req.overheadRate());
        }
        w.setRemark(EngSupport.trim(req.remark()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void setStatus(Long id, boolean enabled) {
        WorkCenterDO w = getOrThrow(id);
        w.setStatus((enabled ? EnableStatus.ENABLED : EnableStatus.DISABLED).name());
        mapper.updateByIdOrFail(w);
    }

    /** 被工艺路线或生产订单引用时不能删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getOrThrow(id);
        boolean used = stepMapper.selectCount(new LambdaQueryWrapper<RoutingStepDO>().eq(RoutingStepDO::getWorkCenterId, id)) > 0
                || referenceCheckers.stream().anyMatch(c -> c.isWorkCenterUsed(id));
        if (used) throw new BizException(EngineeringErrorCodes.WORK_CENTER_IN_USE);
        mapper.deleteById(id);
    }

    public WorkCenterDO getOrThrow(Long id) {
        WorkCenterDO w = id == null ? null : mapper.selectById(id);
        if (w == null) throw new BizException(EngineeringErrorCodes.WORK_CENTER_NOT_EXISTS);
        return w;
    }

    public Map<Long, WorkCenterDO> byIds(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : mapper.selectBatchIds(set).stream().collect(Collectors.toMap(WorkCenterDO::getId, x -> x));
    }

    // ==================== WorkCenterApi ====================

    @Override
    public Optional<WorkCenterDTO> get(Long id) {
        return Optional.ofNullable(id == null ? null : mapper.selectById(id)).map(WorkCenterService::toDto);
    }

    @Override
    public List<WorkCenterDTO> list() {
        return mapper.selectList(new LambdaQueryWrapper<WorkCenterDO>().eq(WorkCenterDO::getStatus, EnableStatus.ENABLED.name())
                .orderByAsc(WorkCenterDO::getCode)).stream().map(WorkCenterService::toDto).toList();
    }

    static WorkCenterDTO toDto(WorkCenterDO w) {
        return new WorkCenterDTO(w.getId(), w.getCode(), w.getName(), w.getDeptId(), w.getWcType(), w.getHoursPerShift(), w.getShiftCount(),
                w.getEfficiencyPct(), capacity(w), w.getLaborRate(), w.getOverheadRate(), EnableStatus.ENABLED.name().equals(w.getStatus()));
    }
}
