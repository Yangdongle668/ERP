package com.erp.module.workbench.service.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.workbench.api.WorkbenchErrorCodes;
import com.erp.module.workbench.config.WorkbenchModuleConfig;
import com.erp.module.workbench.controller.vo.WbVOs.MessageQuery;
import com.erp.module.workbench.controller.vo.WbVOs.MessageVO;
import com.erp.module.workbench.controller.vo.WbVOs.UnreadCount;
import com.erp.module.workbench.dal.dataobject.WbMessageDO;
import com.erp.module.workbench.dal.mapper.WbMessageMapper;
import com.erp.module.workbench.service.WbSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 站内消息（需求 02-03）：监听 {@link MessageSendEvent}（业务事务提交后写入），可同时发邮件 */
@Slf4j
@Service
public class MessageService {

    private final WbMessageMapper mapper;
    private final EmailService emailService;
    private final WbSupport support;

    public MessageService(WbMessageMapper mapper, EmailService emailService, WbSupport support) {
        this.mapper = mapper;
        this.emailService = emailService;
        this.support = support;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessage(MessageSendEvent e) {
        try {
            List<WbMessageDO> saved = new ArrayList<>();
            support.inNewTx(() -> saved.addAll(save(e.getUserIds(), e.getMsgType().name(), e.getTitle(), e.getContent(), e.getRoute())));
            if (e.isSendEmail()) email(saved);
        } catch (RuntimeException ex) {
            log.error("保存消息失败 {}：{}", e.getTitle(), ex.getMessage(), ex);
        }
    }

    /** 保存消息（预警、公告等工作台内部也使用） */
    public List<WbMessageDO> save(Collection<Long> userIds, String type, String title, String content, String route) {
        List<WbMessageDO> list = new ArrayList<>();
        for (Long uid : userIds.stream().filter(Objects::nonNull).distinct().toList()) {
            WbMessageDO m = new WbMessageDO();
            m.setUserId(uid);
            m.setMsgType(type);
            m.setTitle(WbSupport.limit(StringUtils.hasText(title) ? title : "-", 256));
            m.setContent(WbSupport.limit(content, 2000));
            m.setRoute(WbSupport.limit(route, 256));
            m.setReadFlag(false);
            m.setEmailSent(false);
            mapper.insert(m);
            list.add(m);
        }
        return list;
    }

    public void email(List<WbMessageDO> messages) {
        if (messages.isEmpty() || !emailService.enabled()) return;
        Map<Long, UserDTO> users = support.users(messages.stream().map(WbMessageDO::getUserId).toList());
        for (WbMessageDO m : messages) {
            UserDTO u = users.get(m.getUserId());
            if (u != null) emailService.send(u.email(), "[ERP] " + m.getTitle(), m.getContent(), m.getId());
        }
    }

    // ==================== 查询与操作 ====================

    public PageResult<MessageVO> page(MessageQuery q) {
        IPage<WbMessageDO> p = mapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), new LambdaQueryWrapper<WbMessageDO>()
                .eq(WbMessageDO::getUserId, support.currentUser())
                .eq(StringUtils.hasText(q.getType()), WbMessageDO::getMsgType, q.getType())
                .eq(q.getRead() != null, WbMessageDO::getReadFlag, q.getRead())
                .orderByDesc(WbMessageDO::getId));
        return new PageResult<>(p.getRecords().stream().map(MessageService::vo).toList(), p.getTotal());
    }

    static MessageVO vo(WbMessageDO m) {
        return new MessageVO(m.getId(), m.getMsgType(), m.getTitle(), m.getContent(), m.getRoute(), Boolean.TRUE.equals(m.getReadFlag()), m.getReadAt(),
                m.getCreatedAt());
    }

    public UnreadCount unread(Long userId) {
        Map<String, Long> byType = new LinkedHashMap<>();
        for (MessageSendEvent.Type t : MessageSendEvent.Type.values()) {
            byType.put(t.name(), mapper.selectCount(new LambdaQueryWrapper<WbMessageDO>().eq(WbMessageDO::getUserId, userId)
                    .eq(WbMessageDO::getReadFlag, false).eq(WbMessageDO::getMsgType, t.name())));
        }
        return new UnreadCount(byType.values().stream().mapToLong(Long::longValue).sum(), byType);
    }

    @Transactional(rollbackFor = Exception.class)
    public void read(Long id) {
        WbMessageDO m = mapper.selectById(id);
        if (m == null || !m.getUserId().equals(support.currentUser())) throw BizException.of(WorkbenchErrorCodes.NOT_EXISTS, "消息");
        if (Boolean.TRUE.equals(m.getReadFlag())) return;
        m.setReadFlag(true);
        m.setReadAt(LocalDateTime.now());
        mapper.updateByIdOrFail(m);
    }

    @Transactional(rollbackFor = Exception.class)
    public int readAll(String type) {
        return mapper.update(null, new LambdaUpdateWrapper<WbMessageDO>().set(WbMessageDO::getReadFlag, true).set(WbMessageDO::getReadAt, LocalDateTime.now())
                .eq(WbMessageDO::getUserId, support.currentUser()).eq(WbMessageDO::getReadFlag, false)
                .eq(StringUtils.hasText(type), WbMessageDO::getMsgType, type));
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteRead() {
        return mapper.deleteRead(support.currentUser());
    }

    /** WB-MSG-R03：删除超过保留天数的消息 */
    @Transactional(rollbackFor = Exception.class)
    public int cleanup() {
        return mapper.deleteBefore(LocalDateTime.now().minusDays(support.params().getInt(WorkbenchModuleConfig.P_MESSAGE_RETENTION)));
    }
}
