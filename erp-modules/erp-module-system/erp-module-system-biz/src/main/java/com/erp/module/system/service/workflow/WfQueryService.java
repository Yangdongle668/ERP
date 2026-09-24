package com.erp.module.system.service.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.controller.vo.WorkflowVOs.BatchResult;
import com.erp.module.system.controller.vo.WorkflowVOs.ByBizResp;
import com.erp.module.system.controller.vo.WorkflowVOs.InstanceQuery;
import com.erp.module.system.controller.vo.WorkflowVOs.InstanceView;
import com.erp.module.system.controller.vo.WorkflowVOs.MonitorResp;
import com.erp.module.system.controller.vo.WorkflowVOs.MyInstanceResp;
import com.erp.module.system.controller.vo.WorkflowVOs.MyTaskResp;
import com.erp.module.system.controller.vo.WorkflowVOs.PendingTask;
import com.erp.module.system.controller.vo.WorkflowVOs.TaskView;
import com.erp.module.system.dal.dataobject.WfBizTypeDO;
import com.erp.module.system.dal.dataobject.WfInstanceDO;
import com.erp.module.system.dal.dataobject.WfTaskDO;
import com.erp.module.system.dal.mapper.WfInstanceMapper;
import com.erp.module.system.dal.mapper.WfTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 审批查询（需求 08 第 4.2、4.3 节与工作台“我的待办/我发起的”） */
@Service
public class WfQueryService {

    private final WfInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    private final WfDefinitionService definitions;
    private final WorkflowEngine engine;
    private final UserApi userApi;
    private final TransactionTemplate tx;

    public WfQueryService(WfInstanceMapper instanceMapper, WfTaskMapper taskMapper, WfDefinitionService definitions, WorkflowEngine engine,
                          UserApi userApi, TransactionTemplate tx) {
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.definitions = definitions;
        this.engine = engine;
        this.userApi = userApi;
        this.tx = tx;
    }

    /** 单据的全部审批实例（新的在前）+ 当前用户的待处理任务与能否撤回 */
    public ByBizResp byBiz(String bizType, Long bizId, Long me) {
        List<WfInstanceDO> list = instanceMapper.selectList(new LambdaQueryWrapper<WfInstanceDO>().eq(WfInstanceDO::getBizType, bizType)
                .eq(WfInstanceDO::getBizId, bizId).orderByDesc(WfInstanceDO::getStartedAt).orderByDesc(WfInstanceDO::getId));
        List<InstanceView> views = views(list, true);
        Long myTask = null;
        boolean canWithdraw = false;
        Long runningId = null;
        for (WfInstanceDO ins : list) {
            if (!WfInstanceDO.RUNNING.equals(ins.getStatus())) continue;
            runningId = ins.getId();
            canWithdraw = me.equals(ins.getInitiatorId());
            myTask = engine.tasksOf(ins.getId(), null).stream()
                    .filter(t -> WfTaskDO.PENDING.equals(t.getStatus()) && me.equals(t.getAssigneeId())).map(WfTaskDO::getId).findFirst().orElse(null);
        }
        return new ByBizResp(views, myTask, canWithdraw, runningId);
    }

    public InstanceView instance(Long id) {
        return views(List.of(engine.getInstance(id)), true).get(0);
    }

    /** 实例监控（4.2） */
    public PageResult<MonitorResp> monitor(InstanceQuery q) {
        Set<Long> byAssignee = null;
        if (q.getAssigneeId() != null) {
            byAssignee = taskMapper.selectList(new LambdaQueryWrapper<WfTaskDO>().select(WfTaskDO::getInstanceId)
                    .eq(WfTaskDO::getAssigneeId, q.getAssigneeId()).eq(WfTaskDO::getStatus, WfTaskDO.PENDING))
                    .stream().map(WfTaskDO::getInstanceId).collect(Collectors.toSet());
            if (byAssignee.isEmpty()) return PageResult.empty();
        }
        Page<WfInstanceDO> page = instanceMapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), new LambdaQueryWrapper<WfInstanceDO>()
                .eq(StringUtils.hasText(q.getBizType()), WfInstanceDO::getBizType, q.getBizType())
                .likeRight(StringUtils.hasText(q.getBizNo()), WfInstanceDO::getBizNo, q.getBizNo())
                .eq(q.getInitiatorId() != null, WfInstanceDO::getInitiatorId, q.getInitiatorId())
                .eq(StringUtils.hasText(q.getStatus()), WfInstanceDO::getStatus, q.getStatus())
                .ge(q.getStartedFrom() != null, WfInstanceDO::getStartedAt, q.getStartedFrom())
                .le(q.getStartedTo() != null, WfInstanceDO::getStartedAt, q.getStartedTo())
                .in(byAssignee != null, WfInstanceDO::getId, byAssignee)
                .orderByDesc(WfInstanceDO::getStartedAt).orderByDesc(WfInstanceDO::getId));
        List<WfInstanceDO> rows = page.getRecords();
        Map<Long, List<WfTaskDO>> pending = pendingTasks(rows.stream().map(WfInstanceDO::getId).toList());
        Set<Long> userIds = new HashSet<>();
        rows.forEach(r -> userIds.add(r.getInitiatorId()));
        pending.values().forEach(ts -> ts.forEach(t -> userIds.add(t.getAssigneeId())));
        Map<Long, UserDTO> users = userApi.list(userIds);
        Map<String, WfBizTypeDO> types = definitions.bizTypeMap();
        LocalDateTime now = LocalDateTime.now();
        return new PageResult<>(rows.stream().map(r -> {
            List<WfTaskDO> ts = pending.getOrDefault(r.getId(), List.of());
            WfBizTypeDO t = types.get(r.getBizType());
            return new MonitorResp(r.getId(), r.getBizType(), t == null ? r.getBizType() : t.getName(), r.getBizId(), r.getBizNo(), r.getTitle(),
                    name(users, r.getInitiatorId()), r.getStartedAt(), r.getCurrentNodeName(),
                    ts.stream().map(x -> name(users, x.getAssigneeId())).toList(),
                    ts.stream().map(x -> new PendingTask(x.getId(), x.getAssigneeId(), name(users, x.getAssigneeId()))).toList(),
                    WfInstanceDO.RUNNING.equals(r.getStatus()) && r.getNodeStartedAt() != null ? Duration.between(r.getNodeStartedAt(), now).toMinutes() : null,
                    r.getStatus(), engine.route(r));
        }).toList(), page.getTotal());
    }

    /** 我的待办（status=PENDING）/ 已处理（status=DONE） */
    public PageResult<MyTaskResp> myTasks(Long me, String status, int pageNo, int pageSize) {
        boolean pendingOnly = !"DONE".equals(status);
        Page<WfTaskDO> page = taskMapper.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<WfTaskDO>()
                .and(w -> w.eq(WfTaskDO::getAssigneeId, me).or().eq(!pendingOnly, WfTaskDO::getHandledBy, me))
                .eq(pendingOnly, WfTaskDO::getStatus, WfTaskDO.PENDING)
                .in(!pendingOnly, WfTaskDO::getStatus, WfTaskDO.APPROVED, WfTaskDO.REJECTED, WfTaskDO.TRANSFERRED)
                .orderByDesc(pendingOnly ? WfTaskDO::getCreatedAt : WfTaskDO::getHandledAt).orderByDesc(WfTaskDO::getId));
        Map<Long, WfInstanceDO> instances = instances(page.getRecords().stream().map(WfTaskDO::getInstanceId).toList());
        Map<Long, UserDTO> users = userApi.list(instances.values().stream().map(WfInstanceDO::getInitiatorId).distinct().toList());
        Map<String, WfBizTypeDO> types = definitions.bizTypeMap();
        return new PageResult<>(page.getRecords().stream().map(t -> {
            WfInstanceDO i = instances.get(t.getInstanceId());
            WfBizTypeDO bt = i == null ? null : types.get(i.getBizType());
            return new MyTaskResp(t.getId(), t.getInstanceId(), i == null ? null : i.getBizType(), bt == null ? null : bt.getName(),
                    i == null ? null : i.getBizId(), i == null ? null : i.getBizNo(), i == null ? null : i.getTitle(), t.getNodeName(), t.getStatus(),
                    i == null ? null : name(users, i.getInitiatorId()), i == null ? null : i.getStartedAt(), t.getCreatedAt(), t.getHandledAt(),
                    i == null ? null : engine.route(i));
        }).toList(), page.getTotal());
    }

    /** 我发起的 */
    public PageResult<MyInstanceResp> myInstances(Long me, String status, int pageNo, int pageSize) {
        Page<WfInstanceDO> page = instanceMapper.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<WfInstanceDO>()
                .eq(WfInstanceDO::getInitiatorId, me).eq(StringUtils.hasText(status), WfInstanceDO::getStatus, status)
                .orderByDesc(WfInstanceDO::getStartedAt).orderByDesc(WfInstanceDO::getId));
        Map<String, WfBizTypeDO> types = definitions.bizTypeMap();
        return new PageResult<>(page.getRecords().stream().map(i -> new MyInstanceResp(i.getId(), i.getBizType(),
                types.containsKey(i.getBizType()) ? types.get(i.getBizType()).getName() : i.getBizType(), i.getBizId(), i.getBizNo(), i.getTitle(),
                i.getStatus(), i.getCurrentNodeName(), i.getStartedAt(), i.getFinishedAt(), engine.route(i))).toList(), page.getTotal());
    }

    /** 批量通过（P1）：逐条在独立事务中执行，返回每条结果 */
    public List<BatchResult> batchApprove(List<Long> taskIds, String comment, Long me) {
        List<BatchResult> results = new ArrayList<>();
        for (Long id : taskIds) {
            WfTaskDO t = taskMapper.selectById(id);
            String bizNo = t == null ? String.valueOf(id) : instanceMapper.selectById(t.getInstanceId()).getBizNo();
            try {
                tx.executeWithoutResult(s -> engine.approve(id, comment, me));
                results.add(new BatchResult(id, bizNo, true, null));
            } catch (BizException e) {
                results.add(new BatchResult(id, bizNo, false, e.getMessage()));
            }
        }
        return results;
    }

    // ==================== 视图 ====================

    private List<InstanceView> views(List<WfInstanceDO> list, boolean withVariables) {
        if (list.isEmpty()) return List.of();
        Map<Long, List<WfTaskDO>> tasks = taskMapper.selectList(new LambdaQueryWrapper<WfTaskDO>()
                        .in(WfTaskDO::getInstanceId, list.stream().map(WfInstanceDO::getId).toList())
                        .orderByAsc(WfTaskDO::getCreatedAt).orderByAsc(WfTaskDO::getId))
                .stream().collect(Collectors.groupingBy(WfTaskDO::getInstanceId));
        Set<Long> userIds = new HashSet<>();
        list.forEach(i -> userIds.add(i.getInitiatorId()));
        tasks.values().forEach(ts -> ts.forEach(t -> {
            userIds.add(t.getAssigneeId());
            userIds.add(t.getTransferToId());
            userIds.add(t.getHandledBy());
        }));
        userIds.remove(null);
        Map<Long, UserDTO> users = userApi.list(userIds);
        return list.stream().map(i -> new InstanceView(i.getId(), i.getBizType(), i.getBizId(), i.getBizNo(), i.getTitle(), i.getStatus(),
                i.getInitiatorId(), name(users, i.getInitiatorId()), i.getStartedAt(), i.getFinishedAt(), i.getCurrentNodeName(), i.getResultComment(),
                withVariables ? engine.variables(i) : null,
                tasks.getOrDefault(i.getId(), List.of()).stream().map(t -> new TaskView(t.getId(), t.getNodeSeq(), t.getNodeName(), t.getMultiMode(),
                        t.getAssigneeId(), t.getAssigneeId() == null ? "—" : name(users, t.getAssigneeId()), t.getStatus(), t.getComment(),
                        t.getAutoReason(), t.getTransferToId() == null ? null : name(users, t.getTransferToId()),
                        t.getHandledBy() == null || Objects.equals(t.getHandledBy(), t.getAssigneeId()) ? null : name(users, t.getHandledBy()),
                        t.getCreatedAt(), t.getHandledAt())).toList())).toList();
    }

    private Map<Long, List<WfTaskDO>> pendingTasks(Collection<Long> instanceIds) {
        if (instanceIds.isEmpty()) return Map.of();
        return taskMapper.selectList(new LambdaQueryWrapper<WfTaskDO>().in(WfTaskDO::getInstanceId, instanceIds)
                .eq(WfTaskDO::getStatus, WfTaskDO.PENDING).orderByAsc(WfTaskDO::getId)).stream().collect(Collectors.groupingBy(WfTaskDO::getInstanceId));
    }

    private Map<Long, WfInstanceDO> instances(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return instanceMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(WfInstanceDO::getId, i -> i));
    }

    private static String name(Map<Long, UserDTO> users, Long id) {
        if (id == null) return null;
        UserDTO u = users.get(id);
        return u == null ? String.valueOf(id) : u.realName();
    }
}
