package com.erp.module.system.service.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.notify.TodoCreatedEvent;
import com.erp.module.system.api.notify.TodoDoneEvent;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.ApprovalSummary;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import com.erp.module.system.dal.dataobject.WfBizTypeDO;
import com.erp.module.system.dal.dataobject.WfDefinitionDO;
import com.erp.module.system.dal.dataobject.WfInstanceDO;
import com.erp.module.system.dal.dataobject.WfNodeDO;
import com.erp.module.system.dal.dataobject.WfTaskDO;
import com.erp.module.system.dal.mapper.WfInstanceMapper;
import com.erp.module.system.dal.mapper.WfTaskMapper;
import com.erp.module.system.service.support.UserDisabledEvent;
import com.erp.module.system.service.workflow.WfDefinitionService.Model;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 审批引擎（需求 08 第 3 节）：“条件分支 + 顺序节点”。
 *
 * <p>每个动作都先锁定实例行（同一实例上的动作串行），任务状态用条件更新（只有 PENDING 能被处理，R10）。
 * 实例结束时在同一事务内发布 {@link ApprovalCompletedEvent}，业务处理失败时整个动作回滚（R11）。
 */
@Slf4j
@Service
public class WorkflowEngine implements WorkflowApi {

    static final String AUTO_INITIATOR = "发起人本人";
    static final String AUTO_DUPLICATE = "重复审批人";
    static final String AUTO_EMPTY = "无审批人";
    static final String SYSTEM_TRANSFER = "系统转交：原处理人已停用";
    private static final int COMMENT_MAX = 500;

    private final WfDefinitionService definitions;
    private final WfApprovers approvers;
    private final WfInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    private final UserApi userApi;
    private final NotifyApi notifyApi;
    private final DomainEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final OrgApi orgApi;

    public WorkflowEngine(WfDefinitionService definitions, WfApprovers approvers, WfInstanceMapper instanceMapper, WfTaskMapper taskMapper,
                          UserApi userApi, NotifyApi notifyApi, DomainEventPublisher publisher, ObjectMapper objectMapper, OrgApi orgApi) {
        this.definitions = definitions;
        this.approvers = approvers;
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.userApi = userApi;
        this.notifyApi = notifyApi;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.orgApi = orgApi;
    }

    // ==================== 发起 ====================

    @Override
    @Transactional
    public StartResult start(String bizType, Long bizId, String bizNo, String title, Map<String, Object> variables,
                             Map<String, Long> bizUsers, Long initiatorId) {
        Optional<WfBizTypeDO> type = definitions.bizTypeOpt(bizType);
        if (type.isEmpty()) return StartResult.notRequired();
        if (running(bizType, bizId).isPresent()) throw BizException.of(SystemErrorCodes.WF_ALREADY_RUNNING);
        Optional<WfDefinitionDO> active = definitions.activeOf(bizType);
        if (active.isEmpty() || !Boolean.TRUE.equals(active.get().getEnabled())) return StartResult.notRequired();

        Model model = definitions.load(active.get());
        Model.Branch branch = model.branches().stream()
                .filter(b -> b.isDefault() || WfConditions.matches(b.conditions(), definitions.fieldTypes(type.get()), variables, orgApi, bizType))
                .findFirst().orElseThrow(() -> BizException.of(SystemErrorCodes.WF_NO_DEFAULT_BRANCH));

        UserDTO initiator = userApi.get(initiatorId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        WfInstanceDO ins = new WfInstanceDO();
        ins.setBizType(bizType);
        ins.setBizId(bizId);
        ins.setBizNo(bizNo);
        ins.setTitle(title == null || title.isBlank() ? type.get().getName() + " " + bizNo : truncate(title, 128));
        ins.setDefinitionId(model.definition().getId());
        ins.setBranchId(branch.id());
        ins.setInitiatorId(initiatorId);
        ins.setInitiatorDeptId(initiator == null ? null : initiator.deptId());
        ins.setVariables(definitions.json(variables == null ? Map.of() : variables));
        ins.setBizUsers(definitions.json(bizUsers == null ? Map.of() : bizUsers));
        ins.setStatus(WfInstanceDO.RUNNING);
        ins.setStartedAt(now);
        instanceMapper.insert(ins);

        if (advance(ins, model, branch, 1, Set.of())) {
            // 所有节点自动通过：调用方直接审核，不发布结果事件
            ins.setStatus(WfInstanceDO.APPROVED);
            ins.setFinishedAt(LocalDateTime.now());
            ins.setCurrentSeq(null);
            ins.setCurrentNodeName(null);
            instanceMapper.updateByIdOrFail(ins);
            return new StartResult(StartResult.Status.AUTO_APPROVED, ins.getId());
        }
        return new StartResult(StartResult.Status.STARTED, ins.getId());
    }

    /**
     * 从 fromSeq 开始生成节点任务（第 3.1 节）：解析审批人 → 无审批人按 empty_policy → 发起人本人 / 相邻重复审批人自动通过。
     * 节点自动通过时继续下一节点。
     *
     * @param prevApprovers 上一节点的通过人（用于 skip_duplicate）
     * @return true：所有剩余节点都已通过（实例应结束为 APPROVED）
     */
    private boolean advance(WfInstanceDO ins, Model model, Model.Branch branch, int fromSeq, Set<Long> prevApprovers) {
        WfDefinitionDO def = model.definition();
        Map<String, Long> bizUsers = readBizUsers(ins);
        Set<Long> prev = prevApprovers;
        for (WfNodeDO node : branch.nodes()) {
            if (node.getSeq() < fromSeq) continue;
            List<Long> users = approvers.resolve(node.getApproverType(), definitions.tree(node.getApproverValue()), ins.getInitiatorId(),
                    ins.getInitiatorDeptId(), bizUsers);
            String emptyNote = null;
            if (users.isEmpty() && WfDefinitionDO.EMPTY_TO_ADMIN.equals(def.getEmptyPolicy())) {
                users = approvers.admins();
                emptyNote = "无审批人，转流程管理员";
            }
            if (users.isEmpty()) {
                insertTask(ins, node, null, WfTaskDO.AUTO_PASSED, AUTO_EMPTY);
                prev = Set.of();
                continue;
            }
            // 先判断每个审批人是否自动通过
            Map<Long, String> auto = new HashMap<>();
            for (Long u : users) {
                if (Boolean.TRUE.equals(def.getSkipInitiator()) && u.equals(ins.getInitiatorId())) auto.put(u, AUTO_INITIATOR);
                else if (Boolean.TRUE.equals(def.getSkipDuplicate()) && prev.contains(u)) auto.put(u, AUTO_DUPLICATE);
            }
            boolean any = !"ALL".equals(node.getMultiMode());
            boolean passed = auto.size() == users.size() || (any && !auto.isEmpty());
            if (passed) {
                auto.forEach((u, reason) -> insertTask(ins, node, u, WfTaskDO.AUTO_PASSED, reason));
                prev = auto.keySet();
                continue;
            }
            List<WfTaskDO> pending = new ArrayList<>();
            for (Long u : users) {
                if (auto.containsKey(u)) insertTask(ins, node, u, WfTaskDO.AUTO_PASSED, auto.get(u));
                else pending.add(insertTask(ins, node, u, WfTaskDO.PENDING, emptyNote));
            }
            ins.setCurrentSeq(node.getSeq());
            ins.setCurrentNodeName(node.getName());
            ins.setNodeStartedAt(LocalDateTime.now());
            instanceMapper.updateByIdOrFail(ins);
            pending.forEach(t -> todo(ins, t));
            return false;
        }
        return true;
    }

    // ==================== 审批动作 ====================

    /** 通过：或签节点任一人通过即节点通过；会签需全部通过 */
    @Transactional
    public void approve(Long taskId, String comment, Long operatorId) {
        WfTaskDO task = pendingTaskOf(taskId, operatorId);
        WfInstanceDO ins = lockRunning(task.getInstanceId());
        checkComment(comment, false);
        if (taskMapper.handle(taskId, WfTaskDO.APPROVED, blank(comment), null, null, operatorId, LocalDateTime.now()) == 0) {
            throw BizException.of(SystemErrorCodes.WF_TASK_NOT_HANDLER);
        }
        done(taskId, TodoDoneEvent.Result.DONE);
        List<WfTaskDO> nodeTasks = tasksOf(ins.getId(), task.getNodeSeq());
        if ("ALL".equals(task.getMultiMode())) {
            if (nodeTasks.stream().anyMatch(t -> WfTaskDO.PENDING.equals(t.getStatus()))) return;
        } else {
            cancelPending(nodeTasks, operatorId);
        }
        Set<Long> passedBy = new LinkedHashSet<>();
        nodeTasks.stream().filter(t -> WfTaskDO.APPROVED.equals(t.getStatus()) || WfTaskDO.AUTO_PASSED.equals(t.getStatus()))
                .map(WfTaskDO::getAssigneeId).filter(Objects::nonNull).forEach(passedBy::add);
        Model model = definitions.load(ins.getDefinitionId());
        if (advance(ins, model, model.branch(ins.getBranchId()), task.getNodeSeq() + 1, passedBy)) {
            finish(ins, ApprovalCompletedEvent.Result.APPROVED, null, operatorId);
        }
    }

    /** 驳回（R06 意见必填）：实例结束，单据回到草稿 */
    @Transactional
    public void reject(Long taskId, String comment, Long operatorId) {
        checkComment(comment, true);
        WfTaskDO task = pendingTaskOf(taskId, operatorId);
        WfInstanceDO ins = lockRunning(task.getInstanceId());
        if (taskMapper.handle(taskId, WfTaskDO.REJECTED, comment.trim(), null, null, operatorId, LocalDateTime.now()) == 0) {
            throw BizException.of(SystemErrorCodes.WF_TASK_NOT_HANDLER);
        }
        done(taskId, TodoDoneEvent.Result.DONE);
        cancelPending(tasksOf(ins.getId(), null), operatorId);
        finish(ins, ApprovalCompletedEvent.Result.REJECTED, comment.trim(), operatorId);
    }

    /** 转交（R08）：处理人本人或流程管理员（monitor 为 true） */
    @Transactional
    public void transfer(Long taskId, Long toUserId, String comment, Long operatorId, boolean monitor) {
        WfTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !WfTaskDO.PENDING.equals(task.getStatus()) || (!monitor && !operatorId.equals(task.getAssigneeId()))) {
            throw BizException.of(SystemErrorCodes.WF_TASK_NOT_HANDLER);
        }
        checkComment(comment, false);
        WfInstanceDO ins = lockRunning(task.getInstanceId());
        if (toUserId.equals(task.getAssigneeId())) throw BizException.of(SystemErrorCodes.WF_TRANSFER_SELF);
        UserDTO to = userApi.get(toUserId).filter(UserDTO::enabled).orElseThrow(() -> BizException.of(SystemErrorCodes.WF_TRANSFER_TARGET_INVALID));
        boolean duplicate = tasksOf(ins.getId(), task.getNodeSeq()).stream()
                .anyMatch(t -> WfTaskDO.PENDING.equals(t.getStatus()) && toUserId.equals(t.getAssigneeId()));
        if (duplicate) throw BizException.of(SystemErrorCodes.WF_TRANSFER_DUPLICATE, to.realName());
        if (taskMapper.handle(taskId, WfTaskDO.TRANSFERRED, blank(comment), toUserId, null, operatorId, LocalDateTime.now()) == 0) {
            throw BizException.of(SystemErrorCodes.WF_TASK_NOT_HANDLER);
        }
        done(taskId, TodoDoneEvent.Result.CANCELED);
        WfNodeDO node = nodeOf(task);
        todo(ins, insertTask(ins, node, toUserId, WfTaskDO.PENDING, null));
    }

    /** 撤回（R07）：只有发起人，且实例审批中 */
    @Transactional
    public void withdrawInstance(Long instanceId, Long operatorId) {
        WfInstanceDO ins = getInstance(instanceId);
        if (!operatorId.equals(ins.getInitiatorId())) throw BizException.of(SystemErrorCodes.WF_WITHDRAW_NOT_INITIATOR);
        if (!WfInstanceDO.RUNNING.equals(ins.getStatus())) throw BizException.of(SystemErrorCodes.WF_NOT_RUNNING);
        ins = lockRunning(instanceId);
        cancelPending(tasksOf(instanceId, null), operatorId);
        finish(ins, ApprovalCompletedEvent.Result.WITHDRAWN, null, operatorId);
    }

    /** 终止（流程管理员，原因必填） */
    @Transactional
    public void terminate(Long instanceId, String reason, Long operatorId) {
        if (reason == null || reason.isBlank()) throw BizException.of(SystemErrorCodes.WF_TERMINATE_REASON_REQUIRED);
        WfInstanceDO ins = lockRunning(instanceId);
        cancelPending(tasksOf(instanceId, null), operatorId);
        finish(ins, ApprovalCompletedEvent.Result.TERMINATED, reason.trim(), operatorId);
    }

    @Override
    @Transactional
    public void withdraw(String bizType, Long bizId, Long operatorId) {
        WfInstanceDO ins = running(bizType, bizId).orElseThrow(() -> BizException.of(SystemErrorCodes.WF_NOT_RUNNING));
        withdrawInstance(ins.getId(), operatorId);
    }

    @Override
    public boolean isRunning(String bizType, Long bizId) {
        return running(bizType, bizId).isPresent();
    }

    @Override
    public Optional<ApprovalSummary> getRunning(String bizType, Long bizId) {
        return running(bizType, bizId).map(ins -> {
            List<Long> ids = tasksOf(ins.getId(), ins.getCurrentSeq()).stream().filter(t -> WfTaskDO.PENDING.equals(t.getStatus()))
                    .map(WfTaskDO::getAssigneeId).toList();
            Map<Long, UserDTO> users = userApi.list(ids);
            return new ApprovalSummary(ins.getId(), ins.getCurrentNodeName(),
                    ids.stream().map(id -> users.containsKey(id) ? users.get(id).realName() : String.valueOf(id)).toList(),
                    ins.getInitiatorId(), ins.getStartedAt());
        });
    }

    /** R12：用户停用时，其待处理任务转交给主部门负责人（负责人即本人或为空时转给流程管理员） */
    @EventListener
    public void onUserDisabled(UserDisabledEvent event) {
        Long userId = event.userId();
        List<WfTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<WfTaskDO>().eq(WfTaskDO::getAssigneeId, userId)
                .eq(WfTaskDO::getStatus, WfTaskDO.PENDING));
        if (tasks.isEmpty()) return;
        Long dept = userApi.get(userId).map(UserDTO::deptId).orElse(null);
        Long target = approvers.leaderFrom(dept).filter(id -> !id.equals(userId))
                .or(() -> approvers.admins().stream().filter(id -> !id.equals(userId)).findFirst()).orElse(null);
        for (WfTaskDO task : tasks) {
            WfInstanceDO ins = lockRunning(task.getInstanceId());
            boolean alreadyThere = target != null && tasksOf(ins.getId(), task.getNodeSeq()).stream()
                    .anyMatch(t -> WfTaskDO.PENDING.equals(t.getStatus()) && target.equals(t.getAssigneeId()));
            taskMapper.handle(task.getId(), target == null || alreadyThere ? WfTaskDO.CANCELED : WfTaskDO.TRANSFERRED, null,
                    target == null || alreadyThere ? null : target, SYSTEM_TRANSFER, null, LocalDateTime.now());
            done(task.getId(), TodoDoneEvent.Result.CANCELED);
            if (target != null && !alreadyThere) todo(ins, insertTask(ins, nodeOf(task), target, WfTaskDO.PENDING, SYSTEM_TRANSFER));
            else if (target == null) log.warn("[审批流] 用户 {} 停用，任务 {} 找不到可转交的人，已取消", userId, task.getId());
        }
    }

    // ==================== 结束 ====================

    private void finish(WfInstanceDO ins, ApprovalCompletedEvent.Result result, String comment, Long operatorId) {
        ins.setStatus(result.name());
        ins.setFinishedAt(LocalDateTime.now());
        ins.setCurrentSeq(null);
        ins.setCurrentNodeName(null);
        ins.setResultComment(comment);
        instanceMapper.updateByIdOrFail(ins);
        // 同一事务内同步通知业务模块；业务处理抛异常时整个审批动作回滚（R11）
        publisher.publish(new ApprovalCompletedEvent(ins.getBizType(), ins.getBizId(), ins.getId(), result, comment, operatorId));
        if (result != ApprovalCompletedEvent.Result.WITHDRAWN) {
            String bizName = definitions.bizTypeOpt(ins.getBizType()).map(WfBizTypeDO::getName).orElse(ins.getBizType());
            String what = switch (result) {
                case APPROVED -> "已通过";
                case REJECTED -> "被驳回";
                case TERMINATED -> "已被终止";
                default -> result.name();
            };
            notifyApi.message(new MessageSendEvent(List.of(ins.getInitiatorId()), MessageSendEvent.Type.APPROVAL_RESULT,
                    "你提交的" + bizName + " " + ins.getBizNo() + " " + what + (comment == null ? "" : "（" + (result == ApprovalCompletedEvent.Result.TERMINATED ? "原因" : "意见") + "：" + comment + "）"),
                    ins.getTitle(), route(ins), false));
        }
    }

    // ==================== 工具 ====================

    private WfTaskDO insertTask(WfInstanceDO ins, WfNodeDO node, Long assignee, String status, String reason) {
        WfTaskDO t = new WfTaskDO();
        t.setInstanceId(ins.getId());
        t.setNodeSeq(node.getSeq());
        t.setNodeName(node.getName());
        t.setMultiMode(node.getMultiMode());
        t.setAssigneeId(assignee);
        t.setStatus(status);
        t.setAutoReason(reason);
        if (!WfTaskDO.PENDING.equals(status)) t.setHandledAt(LocalDateTime.now());
        taskMapper.insert(t);
        return t;
    }

    private WfNodeDO nodeOf(WfTaskDO task) {
        WfInstanceDO ins = getInstance(task.getInstanceId());
        Model model = definitions.load(ins.getDefinitionId());
        return model.branch(ins.getBranchId()).nodes().stream().filter(n -> n.getSeq().equals(task.getNodeSeq())).findFirst()
                .orElseGet(() -> {
                    WfNodeDO n = new WfNodeDO();
                    n.setSeq(task.getNodeSeq());
                    n.setName(task.getNodeName());
                    n.setMultiMode(task.getMultiMode());
                    return n;
                });
    }

    private void cancelPending(List<WfTaskDO> tasks, Long operatorId) {
        for (WfTaskDO t : tasks) {
            if (WfTaskDO.PENDING.equals(t.getStatus())
                    && taskMapper.handle(t.getId(), WfTaskDO.CANCELED, null, null, null, operatorId, LocalDateTime.now()) > 0) {
                t.setStatus(WfTaskDO.CANCELED);
                done(t.getId(), TodoDoneEvent.Result.CANCELED);
            }
        }
    }

    private WfTaskDO pendingTaskOf(Long taskId, Long operatorId) {
        WfTaskDO task = taskId == null ? null : taskMapper.selectById(taskId);
        if (task == null || !WfTaskDO.PENDING.equals(task.getStatus()) || !operatorId.equals(task.getAssigneeId())) {
            throw BizException.of(SystemErrorCodes.WF_TASK_NOT_HANDLER);
        }
        return task;
    }

    /** 锁定实例并确认仍在审批中 */
    private WfInstanceDO lockRunning(Long instanceId) {
        instanceMapper.lock(instanceId);
        WfInstanceDO ins = getInstance(instanceId);
        if (!WfInstanceDO.RUNNING.equals(ins.getStatus())) throw BizException.of(SystemErrorCodes.WF_INSTANCE_FINISHED);
        return ins;
    }

    WfInstanceDO getInstance(Long id) {
        WfInstanceDO ins = id == null ? null : instanceMapper.selectById(id);
        if (ins == null) throw BizException.of(SystemErrorCodes.WF_INSTANCE_NOT_EXISTS);
        return ins;
    }

    Optional<WfInstanceDO> running(String bizType, Long bizId) {
        return Optional.ofNullable(instanceMapper.selectOne(new LambdaQueryWrapper<WfInstanceDO>().eq(WfInstanceDO::getBizType, bizType)
                .eq(WfInstanceDO::getBizId, bizId).eq(WfInstanceDO::getStatus, WfInstanceDO.RUNNING).last("LIMIT 1")));
    }

    List<WfTaskDO> tasksOf(Long instanceId, Integer nodeSeq) {
        return taskMapper.selectList(new LambdaQueryWrapper<WfTaskDO>().eq(WfTaskDO::getInstanceId, instanceId)
                .eq(nodeSeq != null, WfTaskDO::getNodeSeq, nodeSeq).orderByAsc(WfTaskDO::getCreatedAt).orderByAsc(WfTaskDO::getId));
    }

    private void todo(WfInstanceDO ins, WfTaskDO t) {
        notifyApi.todo(new TodoCreatedEvent(todoKey(t.getId()), List.of(t.getAssigneeId()), TodoCreatedEvent.Category.APPROVAL, ins.getBizType(),
                ins.getBizId(), ins.getBizNo(), ins.getTitle(), route(ins), TodoCreatedEvent.Priority.NORMAL, null));
    }

    private void done(Long taskId, TodoDoneEvent.Result result) {
        notifyApi.done(new TodoDoneEvent(todoKey(taskId), null, result));
    }

    static String todoKey(Long taskId) {
        return "WF_TASK:" + taskId;
    }

    String route(WfInstanceDO ins) {
        return definitions.bizTypeOpt(ins.getBizType()).map(t -> t.getDetailRoute().replace("{id}", String.valueOf(ins.getBizId()))).orElse(null);
    }

    private Map<String, Long> readBizUsers(WfInstanceDO ins) {
        try {
            return ins.getBizUsers() == null ? Map.of() : objectMapper.readValue(ins.getBizUsers(), new TypeReference<Map<String, Long>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    JsonNode variables(WfInstanceDO ins) {
        return definitions.tree(ins.getVariables());
    }

    private static void checkComment(String comment, boolean required) {
        if (required && (comment == null || comment.isBlank())) throw BizException.of(SystemErrorCodes.WF_REJECT_COMMENT_REQUIRED);
        if (comment != null && comment.length() > COMMENT_MAX) throw BizException.of(SystemErrorCodes.WF_COMMENT_TOO_LONG);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
