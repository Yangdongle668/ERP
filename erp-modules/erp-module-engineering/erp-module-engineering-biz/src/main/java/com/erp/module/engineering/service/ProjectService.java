package com.erp.module.engineering.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.StateMachine;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.controller.vo.ProjectVOs.MemberRow;
import com.erp.module.engineering.controller.vo.ProjectVOs.MemberSave;
import com.erp.module.engineering.controller.vo.ProjectVOs.ProjectDetail;
import com.erp.module.engineering.controller.vo.ProjectVOs.ProjectQuery;
import com.erp.module.engineering.controller.vo.ProjectVOs.ProjectRow;
import com.erp.module.engineering.controller.vo.ProjectVOs.ProjectSave;
import com.erp.module.engineering.controller.vo.ProjectVOs.RelatedRow;
import com.erp.module.engineering.controller.vo.ProjectVOs.TaskRow;
import com.erp.module.engineering.controller.vo.ProjectVOs.TaskSave;
import com.erp.module.engineering.dal.dataobject.BomDO;
import com.erp.module.engineering.dal.dataobject.CertificationMaterialDO;
import com.erp.module.engineering.dal.dataobject.EcnDO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.dataobject.ProjectDO;
import com.erp.module.engineering.dal.dataobject.ProjectMemberDO;
import com.erp.module.engineering.dal.dataobject.ProjectTaskDO;
import com.erp.module.engineering.dal.dataobject.SampleDO;
import com.erp.module.engineering.dal.mapper.BomMapper;
import com.erp.module.engineering.dal.mapper.CertificationMapper;
import com.erp.module.engineering.dal.mapper.CertificationMaterialMapper;
import com.erp.module.engineering.dal.mapper.EcnMapper;
import com.erp.module.engineering.dal.mapper.ProjectMapper;
import com.erp.module.engineering.dal.mapper.ProjectMemberMapper;
import com.erp.module.engineering.dal.mapper.ProjectTaskMapper;
import com.erp.module.engineering.dal.mapper.SampleMapper;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 研发项目（需求 05-06）：成员、阶段、任务与进度。进度 = 已完成任务权重 ÷ 未取消任务权重（R03）。
 */
@Service
public class ProjectService {

    public static final String BIZ_TYPE = "ENG_PROJECT";
    public static final String TASK_BIZ_TYPE = "ENG_PROJECT_TASK";
    static final String CODE_RULE = "ENG_PROJECT";
    static final String UPDATE_PERMISSION = "eng:project:update";
    public static final List<String> STAGES = List.of("CONCEPT", "DESIGN", "EVT", "DVT", "PVT", "MP");
    static final Set<String> TYPES = Set.of("NPI", "IMPROVEMENT", "CUSTOMER_CUSTOM");
    static final Set<String> PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");

    public enum Status implements StateMachine.Labeled {
        PLANNING("计划中"), IN_PROGRESS("进行中"), ON_HOLD("已暂停"), COMPLETED("已完成"), CANCELED("已取消");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum Op implements StateMachine.Labeled {
        START("开始"), HOLD("暂停"), RESUME("恢复"), COMPLETE("完成"), CANCEL("取消");

        private final String label;

        Op(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    static final StateMachine<Status, Op> MACHINE = StateMachine.builder(Status.class, Op.class)
            .transition(Status.PLANNING, Op.START, Status.IN_PROGRESS)
            .transition(Status.PLANNING, Op.HOLD, Status.ON_HOLD)
            .transition(Status.IN_PROGRESS, Op.HOLD, Status.ON_HOLD)
            .transition(Status.ON_HOLD, Op.RESUME, Status.IN_PROGRESS)
            .transition(Status.PLANNING, Op.COMPLETE, Status.COMPLETED)
            .transition(Status.IN_PROGRESS, Op.COMPLETE, Status.COMPLETED)
            .transition(Status.PLANNING, Op.CANCEL, Status.CANCELED)
            .transition(Status.IN_PROGRESS, Op.CANCEL, Status.CANCELED)
            .transition(Status.ON_HOLD, Op.CANCEL, Status.CANCELED)
            .build();

    public enum TaskStatus implements StateMachine.Labeled {
        TODO("未开始"), DOING("进行中"), DONE("已完成"), CANCELED("已取消");

        private final String label;

        TaskStatus(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    /** 任务状态：目标状态即动作 */
    static final StateMachine<TaskStatus, TaskStatus> TASK_MACHINE = StateMachine.builder(TaskStatus.class, TaskStatus.class)
            .transition(TaskStatus.TODO, TaskStatus.DOING, TaskStatus.DOING)
            .transition(TaskStatus.TODO, TaskStatus.DONE, TaskStatus.DONE)
            .transition(TaskStatus.TODO, TaskStatus.CANCELED, TaskStatus.CANCELED)
            .transition(TaskStatus.DOING, TaskStatus.TODO, TaskStatus.TODO)
            .transition(TaskStatus.DOING, TaskStatus.DONE, TaskStatus.DONE)
            .transition(TaskStatus.DOING, TaskStatus.CANCELED, TaskStatus.CANCELED)
            .transition(TaskStatus.DONE, TaskStatus.DOING, TaskStatus.DOING)
            .transition(TaskStatus.CANCELED, TaskStatus.TODO, TaskStatus.TODO)
            .build();

    private final ProjectMapper mapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectTaskMapper taskMapper;
    private final SampleMapper sampleMapper;
    private final BomMapper bomMapper;
    private final EcnMapper ecnMapper;
    private final CertificationMapper certMapper;
    private final CertificationMaterialMapper certMaterialMapper;
    private final EngSupport support;
    private final FileApi fileApi;
    private final NotifyApi notifyApi;

    public ProjectService(ProjectMapper mapper, ProjectMemberMapper memberMapper, ProjectTaskMapper taskMapper, SampleMapper sampleMapper,
                          BomMapper bomMapper, EcnMapper ecnMapper, CertificationMapper certMapper, CertificationMaterialMapper certMaterialMapper,
                          EngSupport support, FileApi fileApi, NotifyApi notifyApi) {
        this.mapper = mapper;
        this.memberMapper = memberMapper;
        this.taskMapper = taskMapper;
        this.sampleMapper = sampleMapper;
        this.bomMapper = bomMapper;
        this.ecnMapper = ecnMapper;
        this.certMapper = certMapper;
        this.certMaterialMapper = certMaterialMapper;
        this.support = support;
        this.fileApi = fileApi;
        this.notifyApi = notifyApi;
    }

    // ==================== 项目 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(ProjectSave req) {
        ProjectDO p = new ProjectDO();
        p.setDocNo(support.nextNo(CODE_RULE));
        p.setDocDate(LocalDate.now());
        p.setStatus(DocStatus.DRAFT);
        p.setOwnerId(support.currentUser());
        p.setStage(STAGES.get(0));
        p.setProgressPct(BigDecimal.ZERO);
        p.setProjectStatus(Status.PLANNING.name());
        fill(p, req);
        mapper.insert(p);
        saveMembers(p, req.members());
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProjectSave req) {
        ProjectDO p = getOrThrow(id);
        checkManage(p);
        if (req.version() != null) p.setVersion(req.version());
        fill(p, req);
        mapper.updateByIdOrFail(p);
        saveMembers(p, req.members());
    }

    /** R01 计划结束 ≥ 计划开始 */
    private void fill(ProjectDO p, ProjectSave req) {
        if (req.planEnd().isBefore(req.planStart())) throw new BizException(EngineeringErrorCodes.PROJECT_DATE_RANGE);
        if (!TYPES.contains(req.projectType())) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.BAD_REQUEST, "项目类型");
        if (!support.userExists(req.pmUserId())) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.DATA_NOT_EXISTS, "项目经理");
        if (req.productMaterialId() != null) support.material(req.productMaterialId());
        p.setName(req.name().trim());
        p.setProjectType(req.projectType());
        p.setCustomerId(req.customerId());
        p.setProductMaterialId(req.productMaterialId());
        p.setPmUserId(req.pmUserId());
        p.setPriority(req.priority() != null && PRIORITIES.contains(req.priority()) ? req.priority() : "MEDIUM");
        p.setPlanStart(req.planStart());
        p.setPlanEnd(req.planEnd());
        p.setDescription(EngSupport.trim(req.description()));
    }

    /** R02：项目经理自动成为成员；已被任务使用的成员不能移除 */
    private void saveMembers(ProjectDO p, List<MemberSave> members) {
        Map<Long, String> roles = new LinkedHashMap<>();
        roles.put(p.getPmUserId(), "项目经理");
        if (members != null) {
            for (MemberSave m : members) {
                if (m.userId().equals(p.getPmUserId())) {
                    if (StringUtils.hasText(m.role())) roles.put(m.userId(), m.role().trim());
                } else {
                    roles.put(m.userId(), EngSupport.trim(m.role()));
                }
            }
        }
        Set<Long> owners = taskMapper.selectByParent(p.getId()).stream().filter(t -> !TaskStatus.CANCELED.name().equals(t.getTaskStatus()))
                .map(ProjectTaskDO::getOwnerId).collect(Collectors.toSet());
        for (Long o : owners) {
            if (!roles.containsKey(o)) throw new BizException(EngineeringErrorCodes.PROJECT_TASK_OWNER_NOT_MEMBER);
        }
        memberMapper.deleteByParent(p.getId());
        for (Map.Entry<Long, String> e : roles.entrySet()) {
            ProjectMemberDO m = new ProjectMemberDO();
            m.setProjectId(p.getId());
            m.setUserId(e.getKey());
            m.setMemberRole(e.getValue());
            memberMapper.insert(m);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProjectDO p = getOrThrow(id);
        if (!Status.PLANNING.name().equals(p.getProjectStatus())) throw BizException.of(EngineeringErrorCodes.PROJECT_STATUS, label(p));
        for (ProjectTaskDO t : taskMapper.selectByParent(id)) fileApi.deleteByBiz(TASK_BIZ_TYPE, t.getId());
        taskMapper.deleteByParent(id);
        memberMapper.deleteByParent(id);
        mapper.deleteById(id);
    }

    /** 推进阶段（R04 前端确认未完成任务）：只能推进到后续阶段 */
    @Transactional(rollbackFor = Exception.class)
    public void advanceStage(Long id, String stage) {
        ProjectDO p = getOrThrow(id);
        checkManage(p);
        checkActive(p);
        if (STAGES.indexOf(stage) <= STAGES.indexOf(p.getStage())) throw new BizException(EngineeringErrorCodes.PROJECT_STAGE_INVALID);
        String old = p.getStage();
        p.setStage(stage);
        mapper.updateByIdOrFail(p);
        support.log(BIZ_TYPE, id, p.getDocNo(), "STAGE", "推进阶段", old, stage, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void hold(Long id) {
        ProjectDO p = getOrThrow(id);
        checkManage(p);
        fire(p, Op.HOLD, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id) {
        ProjectDO p = getOrThrow(id);
        checkManage(p);
        fire(p, Op.RESUME, null);
    }

    /** R05：所有任务完成或取消 */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        ProjectDO p = getOrThrow(id);
        checkManage(p);
        long undone = taskMapper.selectByParent(id).stream().filter(t -> !isClosed(t)).count();
        if (undone > 0) throw BizException.of(EngineeringErrorCodes.PROJECT_TASKS_UNDONE, undone);
        p.setActualEnd(LocalDate.now());
        if (p.getActualStart() == null) p.setActualStart(LocalDate.now());
        fire(p, Op.COMPLETE, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        ProjectDO p = getOrThrow(id);
        checkManage(p);
        if (!StringUtils.hasText(reason)) throw new BizException(EngineeringErrorCodes.PROJECT_CANCEL_REASON);
        p.setCancelReason(reason.trim());
        fire(p, Op.CANCEL, reason.trim());
    }

    private void fire(ProjectDO p, Op op, String reason) {
        Status from = Status.valueOf(p.getProjectStatus());
        if (!MACHINE.canFire(from, op)) throw BizException.of(EngineeringErrorCodes.PROJECT_STATUS, from.label());
        Status to = MACHINE.fire(from, op);
        p.setProjectStatus(to.name());
        mapper.updateByIdOrFail(p);
        support.log(BIZ_TYPE, p.getId(), p.getDocNo(), op.name(), op.label(), from.name(), to.name(), reason);
    }

    private static String label(ProjectDO p) {
        return Status.valueOf(p.getProjectStatus()).label();
    }

    private static void checkActive(ProjectDO p) {
        Status s = Status.valueOf(p.getProjectStatus());
        if (s == Status.COMPLETED || s == Status.CANCELED) throw BizException.of(EngineeringErrorCodes.PROJECT_STATUS, s.label());
    }

    /** R06：项目经理或有 eng:project:update 的人 */
    private void checkManage(ProjectDO p) {
        if (!canManage(p)) throw new BizException(EngineeringErrorCodes.PROJECT_NO_PERMISSION);
    }

    private boolean canManage(ProjectDO p) {
        Long me = support.currentUser();
        return me == null || me.equals(p.getPmUserId()) || EngSupport.hasPermission(UPDATE_PERMISSION);
    }

    public ProjectDO getOrThrow(Long id) {
        ProjectDO p = id == null ? null : mapper.selectById(id);
        if (p == null) throw new BizException(EngineeringErrorCodes.PROJECT_NOT_EXISTS);
        return p;
    }

    // ==================== 任务 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long createTask(Long projectId, TaskSave req) {
        ProjectDO p = getOrThrow(projectId);
        checkManage(p);
        checkActive(p);
        ProjectTaskDO t = new ProjectTaskDO();
        t.setProjectId(projectId);
        t.setTaskStatus(TaskStatus.TODO.name());
        fillTask(p, t, req);
        taskMapper.insert(t);
        recompute(p);
        return t.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateTask(Long projectId, Long taskId, TaskSave req) {
        ProjectDO p = getOrThrow(projectId);
        checkManage(p);
        ProjectTaskDO t = getTask(projectId, taskId);
        fillTask(p, t, req);
        taskMapper.updateByIdOrFail(t);
        recompute(p);
    }

    private void fillTask(ProjectDO p, ProjectTaskDO t, TaskSave req) {
        if (req.planEnd().isBefore(req.planStart())) throw new BizException(EngineeringErrorCodes.PROJECT_DATE_RANGE);
        if (!isMember(p.getId(), req.ownerId())) throw new BizException(EngineeringErrorCodes.PROJECT_TASK_OWNER_NOT_MEMBER);
        t.setStage(StringUtils.hasText(req.stage()) && STAGES.contains(req.stage()) ? req.stage() : p.getStage());
        t.setName(req.name().trim());
        t.setOwnerId(req.ownerId());
        t.setPlanStart(req.planStart());
        t.setPlanEnd(req.planEnd());
        t.setDeliverable(EngSupport.trim(req.deliverable()));
        t.setWeight(req.weight() == null ? 1 : req.weight());
        t.setRemark(EngSupport.trim(req.remark()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long projectId, Long taskId) {
        ProjectDO p = getOrThrow(projectId);
        checkManage(p);
        getTask(projectId, taskId);
        taskMapper.deleteById(taskId);
        fileApi.deleteByBiz(TASK_BIZ_TYPE, taskId);
        recompute(p);
    }

    /** 更新任务状态（R06 任务负责人或项目经理）；R08 首个任务变为进行中时项目自动开始 */
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskStatus(Long projectId, Long taskId, String status, String deliverable) {
        ProjectDO p = getOrThrow(projectId);
        checkActive(p);
        ProjectTaskDO t = getTask(projectId, taskId);
        Long me = support.currentUser();
        if (me != null && !me.equals(t.getOwnerId()) && !canManage(p)) throw new BizException(EngineeringErrorCodes.PROJECT_NO_PERMISSION);
        TaskStatus from = TaskStatus.valueOf(t.getTaskStatus());
        TaskStatus to = TaskStatus.valueOf(status);
        if (from != to) t.setTaskStatus(TASK_MACHINE.fire(from, to).name());
        t.setActualEnd(to == TaskStatus.DONE ? (t.getActualEnd() != null ? t.getActualEnd() : LocalDate.now()) : null);
        if (deliverable != null) t.setDeliverable(EngSupport.trim(deliverable));
        taskMapper.updateByIdOrFail(t);
        if ((to == TaskStatus.DOING || to == TaskStatus.DONE) && Status.PLANNING.name().equals(p.getProjectStatus())) {
            p.setActualStart(LocalDate.now());
            fire(p, Op.START, null);
        }
        recompute(p);
    }

    /** R03：进度 = 已完成任务权重 ÷ 未取消任务权重 */
    private void recompute(ProjectDO p) {
        List<ProjectTaskDO> tasks = taskMapper.selectByParent(p.getId());
        int total = tasks.stream().filter(t -> !TaskStatus.CANCELED.name().equals(t.getTaskStatus())).mapToInt(ProjectTaskDO::getWeight).sum();
        int done = tasks.stream().filter(t -> TaskStatus.DONE.name().equals(t.getTaskStatus())).mapToInt(ProjectTaskDO::getWeight).sum();
        BigDecimal pct = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(done).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        ProjectDO fresh = mapper.selectById(p.getId());
        if (fresh.getProgressPct().compareTo(pct) != 0) {
            fresh.setProgressPct(pct);
            mapper.updateByIdOrFail(fresh);
        }
    }

    private boolean isMember(Long projectId, Long userId) {
        return userId != null && memberMapper.selectCount(new LambdaQueryWrapper<ProjectMemberDO>().eq(ProjectMemberDO::getProjectId, projectId)
                .eq(ProjectMemberDO::getUserId, userId)) > 0;
    }

    private ProjectTaskDO getTask(Long projectId, Long taskId) {
        ProjectTaskDO t = taskId == null ? null : taskMapper.selectById(taskId);
        if (t == null || !t.getProjectId().equals(projectId)) throw new BizException(EngineeringErrorCodes.PROJECT_TASK_NOT_EXISTS);
        return t;
    }

    private static boolean isClosed(ProjectTaskDO t) {
        return TaskStatus.DONE.name().equals(t.getTaskStatus()) || TaskStatus.CANCELED.name().equals(t.getTaskStatus());
    }

    // ==================== 查询 ====================

    public PageResult<ProjectRow> page(ProjectQuery q) {
        LambdaQueryWrapper<ProjectDO> w = new LambdaQueryWrapper<ProjectDO>()
                .likeRight(StringUtils.hasText(q.getDocNo()), ProjectDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .like(StringUtils.hasText(q.getName()), ProjectDO::getName, q.getName())
                .eq(q.getCustomerId() != null, ProjectDO::getCustomerId, q.getCustomerId())
                .eq(q.getPmUserId() != null, ProjectDO::getPmUserId, q.getPmUserId())
                .eq(StringUtils.hasText(q.getStage()), ProjectDO::getStage, q.getStage())
                .ge(q.getPlanEndFrom() != null, ProjectDO::getPlanEnd, q.getPlanEndFrom())
                .le(q.getPlanEndTo() != null, ProjectDO::getPlanEnd, q.getPlanEndTo());
        if (StringUtils.hasText(q.getStatuses())) w.in(ProjectDO::getProjectStatus, Arrays.asList(q.getStatuses().split(",")));
        w.orderByDesc(ProjectDO::getId);
        PageResult<ProjectDO> page = mapper.selectPage(q, w);
        List<ProjectDO> list = page.list();
        Map<Long, UserDTO> users = support.users(list.stream().map(ProjectDO::getPmUserId).toList());
        Map<Long, MaterialDO> ms = support.materials(list.stream().map(ProjectDO::getProductMaterialId).toList());
        Map<Long, String> customers = support.customers(list.stream().map(ProjectDO::getCustomerId).toList());
        LocalDate today = LocalDate.now();
        return new PageResult<>(list.stream().map(p -> {
            MaterialDO m = ms.get(p.getProductMaterialId());
            return new ProjectRow(p.getId(), p.getDocNo(), p.getName(), p.getProjectType(), p.getCustomerId(), customers.get(p.getCustomerId()),
                    p.getProductMaterialId(), m == null ? null : m.getCode(), m == null ? null : m.getName(), p.getPmUserId(), EngSupport.name(users, p.getPmUserId()),
                    p.getStage(), p.getProgressPct(), p.getPlanStart(), p.getPlanEnd(), overdue(p, today), p.getPriority(), p.getProjectStatus());
        }).toList(), page.total());
    }

    private static boolean overdue(ProjectDO p, LocalDate today) {
        Status s = Status.valueOf(p.getProjectStatus());
        return s != Status.COMPLETED && s != Status.CANCELED && p.getPlanEnd().isBefore(today);
    }

    public ProjectDetail detail(Long id) {
        ProjectDO p = getOrThrow(id);
        List<ProjectMemberDO> members = memberMapper.selectByParent(id);
        List<ProjectTaskDO> tasks = taskMapper.selectByParent(id);
        Set<Long> uids = new HashSet<>();
        members.forEach(m -> uids.add(m.getUserId()));
        tasks.forEach(t -> uids.add(t.getOwnerId()));
        uids.add(p.getPmUserId());
        uids.add(p.getCreatedBy());
        Map<Long, UserDTO> users = support.users(uids);
        MaterialDO product = p.getProductMaterialId() == null ? null : support.materials(List.of(p.getProductMaterialId())).get(p.getProductMaterialId());
        LocalDate today = LocalDate.now();
        Long me = support.currentUser();
        boolean manage = canManage(p);
        List<TaskRow> taskRows = tasks.stream().sorted((a, b) -> {
            int c = Integer.compare(STAGES.indexOf(a.getStage()), STAGES.indexOf(b.getStage()));
            return c != 0 ? c : a.getPlanStart().compareTo(b.getPlanStart());
        }).map(t -> new TaskRow(t.getId(), t.getStage(), t.getName(), t.getOwnerId(), EngSupport.name(users, t.getOwnerId()), t.getPlanStart(),
                t.getPlanEnd(), t.getActualEnd(), t.getTaskStatus(), t.getDeliverable(), t.getWeight(), t.getRemark(),
                !isClosed(t) && t.getPlanEnd().isBefore(today), fileApi.list(TASK_BIZ_TYPE, t.getId()).size(),
                manage || (me != null && me.equals(t.getOwnerId())))).toList();
        int undoneInStage = (int) tasks.stream().filter(t -> t.getStage().equals(p.getStage()) && !isClosed(t)).count();
        int undoneTotal = (int) tasks.stream().filter(t -> !isClosed(t)).count();
        return new ProjectDetail(p.getId(), p.getDocNo(), p.getName(), p.getProjectType(), p.getCustomerId(),
                support.customers(p.getCustomerId() == null ? List.of() : List.of(p.getCustomerId())).get(p.getCustomerId()), p.getProductMaterialId(),
                product == null ? null : product.getCode(), product == null ? null : product.getName(), p.getPmUserId(), EngSupport.name(users, p.getPmUserId()),
                p.getStage(), p.getProgressPct(), p.getPlanStart(), p.getPlanEnd(), p.getActualStart(), p.getActualEnd(), p.getPriority(), p.getProjectStatus(),
                p.getDescription(), p.getCancelReason(),
                members.stream().map(m -> new MemberRow(m.getUserId(), EngSupport.name(users, m.getUserId()),
                        users.containsKey(m.getUserId()) ? users.get(m.getUserId()).deptName() : null, m.getMemberRole())).toList(),
                taskRows, related(p), undoneInStage, undoneTotal, manage, EngSupport.name(users, p.getCreatedBy()), p.getCreatedAt(), p.getVersion());
    }

    /** 关联：样品单（按项目）、BOM、ECN、认证（按产品物料） */
    private List<RelatedRow> related(ProjectDO p) {
        List<RelatedRow> rows = new ArrayList<>();
        sampleMapper.selectList(new LambdaQueryWrapper<SampleDO>().eq(SampleDO::getProjectId, p.getId()).orderByDesc(SampleDO::getId))
                .forEach(s -> rows.add(new RelatedRow("SAMPLE", s.getId(), s.getDocNo(), s.getPurpose(), s.getSampleStatus())));
        Long mid = p.getProductMaterialId();
        if (mid == null) return rows;
        List<BomDO> boms = bomMapper.selectByMaterial(mid);
        boms.forEach(b -> rows.add(new RelatedRow("BOM", b.getId(), b.getDocNo(), b.getDescription(), b.getStatus().name())));
        Set<Long> bomIds = boms.stream().map(BomDO::getId).collect(Collectors.toSet());
        if (!bomIds.isEmpty()) {
            String in = bomIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            ecnMapper.selectList(new LambdaQueryWrapper<EcnDO>().inSql(EcnDO::getId, "SELECT ecn_id FROM eng_ecn_line WHERE deleted = 0 AND bom_id IN (" + in + ")")
                    .orderByDesc(EcnDO::getId)).forEach(e -> rows.add(new RelatedRow("ECN", e.getId(), e.getDocNo(), e.getTitle(), e.getStatus().name())));
        }
        List<Long> certIds = certMaterialMapper.selectList(new LambdaQueryWrapper<CertificationMaterialDO>().eq(CertificationMaterialDO::getMaterialId, mid))
                .stream().map(CertificationMaterialDO::getCertificationId).toList();
        if (!certIds.isEmpty()) {
            certMapper.selectBatchIds(certIds).forEach(c -> rows.add(new RelatedRow("CERT", c.getId(), c.getCertType() + " " + c.getCertNo(), c.getName(),
                    c.getCertStatus())));
        }
        return rows;
    }

    // ==================== 提醒（R07） ====================

    /** 每天 09:00 给任务负责人推送“明天到期”和“已逾期”的任务提醒 */
    @ErpJob(code = "ENG_PROJECT_TASK_REMIND", name = "项目任务到期提醒", cron = "0 0 9 * * ?")
    public String remindTasks() {
        LocalDate today = LocalDate.now();
        List<ProjectTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<ProjectTaskDO>()
                .in(ProjectTaskDO::getTaskStatus, TaskStatus.TODO.name(), TaskStatus.DOING.name()).le(ProjectTaskDO::getPlanEnd, today.plusDays(1)));
        if (tasks.isEmpty()) return "没有需要提醒的任务";
        Map<Long, ProjectDO> projects = mapper.selectBatchIds(tasks.stream().map(ProjectTaskDO::getProjectId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ProjectDO::getId, x -> x));
        int n = 0;
        for (ProjectTaskDO t : tasks) {
            ProjectDO p = projects.get(t.getProjectId());
            if (p == null || !Objects.equals(p.getProjectStatus(), Status.IN_PROGRESS.name()) && !Objects.equals(p.getProjectStatus(), Status.PLANNING.name())) continue;
            boolean overdue = t.getPlanEnd().isBefore(today);
            if (!overdue && !t.getPlanEnd().equals(today.plusDays(1))) continue;
            notifyApi.message(new MessageSendEvent(List.of(t.getOwnerId()), MessageSendEvent.Type.REMIND,
                    (overdue ? "任务已逾期：" : "任务明天到期：") + t.getName(), "项目 " + p.getDocNo() + " " + p.getName() + "，计划完成 " + t.getPlanEnd(),
                    "/engineering/project/" + p.getId(), false));
            n++;
        }
        return "发出提醒 " + n + " 条";
    }
}
