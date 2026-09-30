package com.erp.module.workbench.service.todo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.system.api.notify.TodoCreatedEvent;
import com.erp.module.system.api.notify.TodoDoneEvent;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.user.UserDeactivatedEvent;
import com.erp.module.system.api.workflow.WorkflowApi;
import com.erp.module.workbench.api.WorkbenchErrorCodes;
import com.erp.module.workbench.config.WorkbenchModuleConfig;
import com.erp.module.workbench.controller.vo.WbVOs.TodoQuery;
import com.erp.module.workbench.controller.vo.WbVOs.TodoVO;
import com.erp.module.workbench.dal.dataobject.WbTodoDO;
import com.erp.module.workbench.dal.mapper.WbTodoMapper;
import com.erp.module.workbench.service.WbSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 待办（需求 02-02）：由 {@link TodoCreatedEvent} / {@link TodoDoneEvent} 驱动（业务事务提交后写入，独立事务）；
 * 同一 todoKey + 用户重复创建时更新（WB-TODO-R02）；审批类待办每天与审批流对账（R04）；用户停用时任务类待办转给部门负责人（R03）。
 */
@Slf4j
@Service
public class TodoService {

    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";
    public static final String CANCELED = "CANCELED";
    public static final String APPROVAL = "APPROVAL";
    public static final String WF_PREFIX = "WF_TASK:";

    private final WbTodoMapper mapper;
    private final WorkflowApi workflowApi;
    private final WbSupport support;

    public TodoService(WbTodoMapper mapper, WorkflowApi workflowApi, WbSupport support) {
        this.mapper = mapper;
        this.workflowApi = workflowApi;
        this.support = support;
    }

    // ==================== 事件 ====================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCreated(TodoCreatedEvent e) {
        try {
            support.inNewTx(() -> create(e));
        } catch (RuntimeException ex) {
            log.error("保存待办失败 {}：{}", e.getTodoKey(), ex.getMessage(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDone(TodoDoneEvent e) {
        try {
            support.inNewTx(() -> finish(e.getTodoKey(), e.getUserIds(), e.getResult() == TodoDoneEvent.Result.CANCELED ? CANCELED : DONE));
        } catch (RuntimeException ex) {
            log.error("完成待办失败 {}：{}", e.getTodoKey(), ex.getMessage(), ex);
        }
    }

    /** WB-TODO-R03：用户停用时，其任务类待办转给部门负责人（审批待办由审批流转交） */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserDeactivated(UserDeactivatedEvent e) {
        try {
            support.inNewTx(() -> reassign(e.getUserId()));
        } catch (RuntimeException ex) {
            log.error("转交停用用户待办失败 {}：{}", e.getUserId(), ex.getMessage(), ex);
        }
    }

    void create(TodoCreatedEvent e) {
        for (Long userId : e.getUserIds().stream().filter(Objects::nonNull).distinct().toList()) {
            WbTodoDO t = mapper.selectOne(new LambdaQueryWrapper<WbTodoDO>().eq(WbTodoDO::getTodoKey, e.getTodoKey()).eq(WbTodoDO::getUserId, userId));
            boolean isNew = t == null;
            if (isNew) {
                t = new WbTodoDO();
                t.setTodoKey(e.getTodoKey());
                t.setUserId(userId);
            }
            t.setCategory(e.getCategory().name());
            t.setBizType(e.getBizType());
            t.setBizId(e.getBizId());
            t.setBizNo(e.getBizNo());
            t.setTitle(WbSupport.limit(StringUtils.hasText(e.getTitle()) ? e.getTitle() : e.getTodoKey(), 256));
            t.setRoute(WbSupport.limit(e.getRoute(), 256));
            t.setPriority(e.getPriority().name());
            t.setDueTime(e.getDueTime());
            t.setTodoStatus(PENDING);
            t.setDoneAt(null);
            if (isNew) {
                try {
                    mapper.insert(t);
                } catch (DuplicateKeyException dup) {
                    log.debug("待办已存在 {} {}", e.getTodoKey(), userId);
                }
            } else {
                mapper.updateById(t);
            }
        }
    }

    void finish(String todoKey, List<Long> userIds, String result) {
        mapper.update(null, new LambdaUpdateWrapper<WbTodoDO>().set(WbTodoDO::getTodoStatus, result).set(WbTodoDO::getDoneAt, LocalDateTime.now())
                .eq(WbTodoDO::getTodoKey, todoKey).eq(WbTodoDO::getTodoStatus, PENDING)
                .in(userIds != null && !userIds.isEmpty(), WbTodoDO::getUserId, userIds));
    }

    void reassign(Long userId) {
        List<WbTodoDO> list = mapper.selectList(new LambdaQueryWrapper<WbTodoDO>().eq(WbTodoDO::getUserId, userId).eq(WbTodoDO::getTodoStatus, PENDING)
                .ne(WbTodoDO::getCategory, APPROVAL));
        if (list.isEmpty()) return;
        Long leader = support.userApi().get(userId).map(UserDTO::deptId).flatMap(d -> support.userApi().getDeptLeader(d))
                .map(UserDTO::id).filter(id -> !id.equals(userId)).orElse(null);
        for (WbTodoDO t : list) {
            boolean exists = leader != null && mapper.selectCount(new LambdaQueryWrapper<WbTodoDO>().eq(WbTodoDO::getTodoKey, t.getTodoKey())
                    .eq(WbTodoDO::getUserId, leader)) > 0;
            if (leader == null || exists) {
                t.setTodoStatus(CANCELED);
                t.setDoneAt(LocalDateTime.now());
            } else {
                t.setUserId(leader);
            }
            mapper.updateById(t);
        }
    }

    // ==================== 查询 ====================

    public PageResult<TodoVO> page(TodoQuery q) {
        Long me = support.currentUser();
        boolean done = DONE.equals(q.getStatus());
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<WbTodoDO> w = new LambdaQueryWrapper<WbTodoDO>().eq(WbTodoDO::getUserId, me)
                .eq(!done, WbTodoDO::getTodoStatus, PENDING)
                .in(done, WbTodoDO::getTodoStatus, DONE, CANCELED)
                .ge(done, WbTodoDO::getDoneAt, now.minusDays(90))
                .eq(StringUtils.hasText(q.getCategory()), WbTodoDO::getCategory, q.getCategory())
                .eq(StringUtils.hasText(q.getBizType()), WbTodoDO::getBizType, q.getBizType())
                .and(StringUtils.hasText(q.getKeyword()), x -> x.like(WbTodoDO::getTitle, q.getKeyword()).or().like(WbTodoDO::getBizNo, q.getKeyword()))
                .ge(q.getCreatedFrom() != null, WbTodoDO::getCreatedAt, q.getCreatedFrom())
                .le(q.getCreatedTo() != null, WbTodoDO::getCreatedAt, q.getCreatedTo())
                .lt(Boolean.TRUE.equals(q.getOverdue()), WbTodoDO::getDueTime, now);
        if (done) w.orderByDesc(WbTodoDO::getDoneAt);
        else w.last("ORDER BY CASE priority WHEN 'HIGH' THEN 0 WHEN 'NORMAL' THEN 1 ELSE 2 END, CASE WHEN due_time IS NULL THEN 1 ELSE 0 END, due_time, created_at DESC");
        IPage<WbTodoDO> p = mapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(p.getRecords().stream().map(t -> vo(t, now)).toList(), p.getTotal());
    }

    static TodoVO vo(WbTodoDO t, LocalDateTime now) {
        Long taskId = null;
        if (t.getTodoKey().startsWith(WF_PREFIX)) {
            try {
                taskId = Long.valueOf(t.getTodoKey().substring(WF_PREFIX.length()));
            } catch (NumberFormatException ignored) {
                // 非数字键不关联审批任务
            }
        }
        boolean overdue = PENDING.equals(t.getTodoStatus()) && t.getDueTime() != null && t.getDueTime().isBefore(now);
        return new TodoVO(t.getId(), t.getTodoKey(), t.getCategory(), t.getBizType(), t.getBizNo(), t.getBizId(), t.getTitle(), t.getRoute(), t.getPriority(),
                t.getDueTime(), overdue, t.getTodoStatus(), t.getCreatedAt(), t.getDoneAt(), taskId, !APPROVAL.equals(t.getCategory()), t.getRoute());
    }

    public long count(Long userId, String category) {
        return mapper.selectCount(new LambdaQueryWrapper<WbTodoDO>().eq(WbTodoDO::getUserId, userId).eq(WbTodoDO::getTodoStatus, PENDING)
                .eq(category != null, WbTodoDO::getCategory, category));
    }

    /** 手工标记完成（任务类，如催料）；审批类必须在单据或审批流中处理 */
    @Transactional(rollbackFor = Exception.class)
    public void markDone(Long id) {
        WbTodoDO t = mapper.selectById(id);
        if (t == null || !t.getUserId().equals(support.currentUser())) throw BizException.of(WorkbenchErrorCodes.NOT_EXISTS, "待办");
        if (APPROVAL.equals(t.getCategory())) throw new BizException(WorkbenchErrorCodes.TODO_NOT_MANUAL);
        if (!PENDING.equals(t.getTodoStatus())) return;
        t.setTodoStatus(DONE);
        t.setDoneAt(LocalDateTime.now());
        mapper.updateByIdOrFail(t);
    }

    // ==================== 定时任务 ====================

    /** WB-TODO-R04：仍为 PENDING 的审批待办与审批流任务状态不一致时修正；返回修正条数 */
    @Transactional(rollbackFor = Exception.class)
    public int reconcile() {
        List<WbTodoDO> list = mapper.selectList(new LambdaQueryWrapper<WbTodoDO>().eq(WbTodoDO::getTodoStatus, PENDING)
                .likeRight(WbTodoDO::getTodoKey, WF_PREFIX));
        if (list.isEmpty()) return 0;
        Map<Long, String> statuses = workflowApi.getTaskStatuses(list.stream().map(t -> vo(t, LocalDateTime.now()).taskId()).filter(Objects::nonNull).toList());
        int fixed = 0;
        for (WbTodoDO t : list) {
            Long taskId = vo(t, LocalDateTime.now()).taskId();
            String s = taskId == null ? null : statuses.get(taskId);
            if (PENDING.equals(s)) continue;
            t.setTodoStatus(s == null || "CANCELED".equals(s) || "TRANSFERRED".equals(s) ? CANCELED : DONE);
            t.setDoneAt(LocalDateTime.now());
            mapper.updateById(t);
            fixed++;
        }
        return fixed;
    }

    /** WB-TODO-R05：删除超过保留天数的已处理待办 */
    @Transactional(rollbackFor = Exception.class)
    public int cleanup() {
        int days = support.params().getInt(WorkbenchModuleConfig.P_TODO_RETENTION);
        return mapper.deleteDoneBefore(LocalDateTime.now().minusDays(days));
    }
}
