package com.erp.module.workbench.service.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.notify.AlertResolvedEvent;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.workbench.api.WorkbenchErrorCodes;
import com.erp.module.workbench.controller.vo.WbVOs.AlertQuery;
import com.erp.module.workbench.controller.vo.WbVOs.AlertStats;
import com.erp.module.workbench.controller.vo.WbVOs.AlertVO;
import com.erp.module.workbench.dal.dataobject.WbAlertDO;
import com.erp.module.workbench.dal.dataobject.WbAlertUserDO;
import com.erp.module.workbench.dal.dataobject.WbMessageDO;
import com.erp.module.workbench.dal.mapper.WbAlertMapper;
import com.erp.module.workbench.dal.mapper.WbAlertUserMapper;
import com.erp.module.workbench.service.WbSupport;
import com.erp.module.workbench.service.message.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 预警中心（需求 02-04）：同一 alertKey 只有一条预警（WB-ALT-R01），条件消除自动关闭（R02），
 * 新建 / 重新打开 / 级别升高为严重时给接收人发站内消息（R03），用户只看接收人包含自己的预警（R04）。
 */
@Slf4j
@Service
public class AlertService {

    public static final String OPEN = "OPEN";
    public static final String HANDLED = "HANDLED";
    public static final String IGNORED = "IGNORED";
    public static final String RESOLVED = "RESOLVED";
    static final int IGNORE_DAYS = 7;

    private final WbAlertMapper mapper;
    private final WbAlertUserMapper userMapper;
    private final MessageService messageService;
    private final WbSupport support;

    public AlertService(WbAlertMapper mapper, WbAlertUserMapper userMapper, MessageService messageService, WbSupport support) {
        this.mapper = mapper;
        this.userMapper = userMapper;
        this.messageService = messageService;
        this.support = support;
    }

    // ==================== 事件 ====================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRaised(AlertRaisedEvent e) {
        try {
            List<WbMessageDO> messages = new ArrayList<>();
            support.inNewTx(() -> messages.addAll(raise(e)));
            messageService.email(messages);
        } catch (RuntimeException ex) {
            log.error("保存预警失败 {}：{}", e.getAlertKey(), ex.getMessage(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onResolved(AlertResolvedEvent e) {
        try {
            support.inNewTx(() -> resolve(e.getAlertKey()));
        } catch (RuntimeException ex) {
            log.error("关闭预警失败 {}：{}", e.getAlertKey(), ex.getMessage(), ex);
        }
    }

    static int rank(String level) {
        return switch (level == null ? "" : level) {
            case "CRITICAL" -> 3;
            case "WARNING" -> 2;
            default -> 1;
        };
    }

    /** @return 需要发邮件的站内消息 */
    List<WbMessageDO> raise(AlertRaisedEvent e) {
        LocalDateTime now = LocalDateTime.now();
        String level = e.getLevel() == null ? "WARNING" : e.getLevel().name();
        List<Long> receivers = !e.getUserIds().isEmpty() ? e.getUserIds()
                : StringUtils.hasText(e.getPermission()) ? support.usersWithPermission(e.getPermission()) : List.of();
        WbAlertDO a = mapper.selectOne(new LambdaQueryWrapper<WbAlertDO>().eq(WbAlertDO::getAlertKey, e.getAlertKey()));
        boolean notify;
        if (a == null) {
            a = new WbAlertDO();
            a.setAlertKey(e.getAlertKey());
            a.setFirstRaisedAt(now);
            a.setAlertStatus(OPEN);
            fill(a, e, level, now);
            mapper.insert(a);
            notify = true;
        } else {
            boolean higher = rank(level) > rank(a.getLevel());
            String status = a.getAlertStatus();
            boolean reopen = switch (status) {
                case RESOLVED -> true;
                case HANDLED -> higher;
                case IGNORED -> higher || a.getIgnoreUntil() == null || now.isAfter(a.getIgnoreUntil());
                default -> false;
            };
            if (RESOLVED.equals(status)) a.setFirstRaisedAt(now);
            if (reopen) {
                a.setAlertStatus(OPEN);
                a.setHandledBy(null);
                a.setHandledAt(null);
                a.setHandleRemark(null);
                a.setIgnoreUntil(null);
            }
            if (OPEN.equals(a.getAlertStatus())) fill(a, e, level, now);
            else a.setLastRaisedAt(now);
            mapper.updateById(a);
            notify = reopen || (OPEN.equals(status) && higher);
        }
        Set<Long> existing = new HashSet<>(userMapper.selectList(new LambdaQueryWrapper<WbAlertUserDO>().eq(WbAlertUserDO::getAlertId, a.getId()))
                .stream().map(WbAlertUserDO::getUserId).toList());
        for (Long uid : receivers.stream().filter(Objects::nonNull).distinct().toList()) {
            if (existing.add(uid)) {
                WbAlertUserDO u = new WbAlertUserDO();
                u.setAlertId(a.getId());
                u.setUserId(uid);
                userMapper.insert(u);
            }
        }
        if (notify && "CRITICAL".equals(a.getLevel()) && !existing.isEmpty()) {
            return messageService.save(existing, MessageSendEvent.Type.REMIND.name(), "【严重预警】" + a.getTitle(), a.getContent(), "/workbench/alert");
        }
        return List.of();
    }

    private static void fill(WbAlertDO a, AlertRaisedEvent e, String level, LocalDateTime now) {
        a.setAlertType(WbSupport.limit(StringUtils.hasText(e.getAlertType()) ? e.getAlertType() : "OTHER", 32));
        a.setLevel(level);
        a.setBizType(e.getBizType());
        a.setBizId(e.getBizId());
        a.setTitle(WbSupport.limit(StringUtils.hasText(e.getTitle()) ? e.getTitle() : e.getAlertKey(), 256));
        a.setContent(WbSupport.limit(e.getContent(), 2000));
        a.setRoute(WbSupport.limit(e.getRoute(), 256));
        a.setLastRaisedAt(now);
    }

    void resolve(String alertKey) {
        WbAlertDO a = mapper.selectOne(new LambdaQueryWrapper<WbAlertDO>().eq(WbAlertDO::getAlertKey, alertKey));
        if (a == null || RESOLVED.equals(a.getAlertStatus())) return;
        a.setAlertStatus(RESOLVED);
        mapper.updateById(a);
    }

    // ==================== 查询与处理 ====================

    private LambdaQueryWrapper<WbAlertDO> mine() {
        return new LambdaQueryWrapper<WbAlertDO>().inSql(WbAlertDO::getId,
                "SELECT alert_id FROM wb_alert_user WHERE deleted = 0 AND user_id = " + support.currentUser());
    }

    public PageResult<AlertVO> page(AlertQuery q) {
        List<String> statuses = StringUtils.hasText(q.getStatuses()) ? Arrays.stream(q.getStatuses().split(",")).map(String::trim).toList() : List.of(OPEN);
        IPage<WbAlertDO> p = mapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), mine()
                .in(!statuses.contains("ALL"), WbAlertDO::getAlertStatus, statuses)
                .eq(StringUtils.hasText(q.getLevel()), WbAlertDO::getLevel, q.getLevel())
                .eq(StringUtils.hasText(q.getAlertType()), WbAlertDO::getAlertType, q.getAlertType())
                .ge(q.getDateFrom() != null, WbAlertDO::getLastRaisedAt, q.getDateFrom())
                .le(q.getDateTo() != null, WbAlertDO::getLastRaisedAt, q.getDateTo())
                .last("ORDER BY CASE level WHEN 'CRITICAL' THEN 0 WHEN 'WARNING' THEN 1 ELSE 2 END, last_raised_at DESC"));
        Map<Long, UserDTO> users = support.users(p.getRecords().stream().map(WbAlertDO::getHandledBy).toList());
        return new PageResult<>(p.getRecords().stream().map(a -> new AlertVO(a.getId(), a.getAlertKey(), a.getAlertType(), a.getLevel(), a.getBizType(),
                a.getBizId(), a.getTitle(), a.getContent(), a.getRoute(), a.getAlertStatus(), a.getFirstRaisedAt(), a.getLastRaisedAt(),
                WbSupport.name(users, a.getHandledBy()), a.getHandledAt(), a.getHandleRemark())).toList(), p.getTotal());
    }

    public AlertStats stats() {
        return new AlertStats(countOpen("CRITICAL"), countOpen("WARNING"), countOpen("INFO"));
    }

    private long countOpen(String level) {
        return mapper.selectCount(mine().eq(WbAlertDO::getAlertStatus, OPEN).eq(WbAlertDO::getLevel, level));
    }

    public long openCount() {
        return mapper.selectCount(mine().eq(WbAlertDO::getAlertStatus, OPEN));
    }

    /** 最新的未处理预警（首页） */
    public List<AlertVO> latest(int n) {
        AlertQuery q = new AlertQuery();
        q.setPageSize(n);
        return page(q).list();
    }

    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, String remark) {
        WbAlertDO a = own(id);
        a.setHandleRemark(WbSupport.limit(WbSupport.requireText(remark, "处理说明"), 500));
        close(a, HANDLED, null);
    }

    /** 忽略：7 天内同一 alertKey 同级别不再提醒 */
    @Transactional(rollbackFor = Exception.class)
    public void ignore(Long id, String reason) {
        WbAlertDO a = own(id);
        a.setHandleRemark(WbSupport.limit(WbSupport.requireText(reason, "忽略原因"), 500));
        close(a, IGNORED, LocalDateTime.now().plusDays(IGNORE_DAYS));
    }

    private void close(WbAlertDO a, String status, LocalDateTime ignoreUntil) {
        if (!OPEN.equals(a.getAlertStatus())) throw BizException.of(WorkbenchErrorCodes.STATUS_NOT_ALLOWED, label(a.getAlertStatus()), HANDLED.equals(status) ? "处理" : "忽略");
        a.setAlertStatus(status);
        a.setHandledBy(support.currentUser());
        a.setHandledAt(LocalDateTime.now());
        a.setIgnoreUntil(ignoreUntil);
        mapper.updateByIdOrFail(a);
    }

    private WbAlertDO own(Long id) {
        WbAlertDO a = id == null ? null : mapper.selectById(id);
        boolean mine = a != null && userMapper.selectCount(new LambdaQueryWrapper<WbAlertUserDO>().eq(WbAlertUserDO::getAlertId, id)
                .eq(WbAlertUserDO::getUserId, support.currentUser())) > 0;
        if (!mine) throw BizException.of(WorkbenchErrorCodes.NOT_EXISTS, "预警");
        return a;
    }

    static String label(String status) {
        return switch (status) {
            case OPEN -> "未处理";
            case HANDLED -> "已处理";
            case IGNORED -> "已忽略";
            default -> "已消除";
        };
    }
}
