package com.erp.module.workbench.service.message;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.erp.module.workbench.config.WorkbenchModuleConfig;
import com.erp.module.workbench.dal.dataobject.WbMessageDO;
import com.erp.module.workbench.dal.mapper.WbMessageMapper;
import com.erp.module.workbench.service.WbSupport;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 邮件通知（WB-MSG-R01 / R02）：参数 wb.email.enabled 为是且配置了 SMTP（spring.mail.*）时异步发送；
 * 失败按 1、5、15 分钟重试 3 次，仍失败只记日志，不影响站内消息；用户未配置邮箱不发送。
 */
@Slf4j
@Service
public class EmailService {

    static final long[] RETRY_MINUTES = {1, 5, 15};

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final WbMessageMapper messageMapper;
    private final WbSupport support;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "wb-mail");
        t.setDaemon(true);
        return t;
    });

    public EmailService(ObjectProvider<JavaMailSender> senderProvider, WbMessageMapper messageMapper, WbSupport support) {
        this.senderProvider = senderProvider;
        this.messageMapper = messageMapper;
        this.support = support;
    }

    public boolean enabled() {
        return support.params().getBool(WorkbenchModuleConfig.P_EMAIL_ENABLED) && senderProvider.getIfAvailable() != null;
    }

    /** 异步发送；messageId 用于成功后标记 email_sent */
    public void send(String to, String subject, String text, Long messageId) {
        if (!StringUtils.hasText(to) || !enabled()) return;
        executor.execute(() -> attempt(to, subject, text, messageId, 0));
    }

    private void attempt(String to, String subject, String text, Long messageId, int retried) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null) return;
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setTo(to);
            m.setSubject(subject);
            m.setText(text == null ? "" : text);
            sender.send(m);
            if (messageId != null) {
                messageMapper.update(null, new LambdaUpdateWrapper<WbMessageDO>().set(WbMessageDO::getEmailSent, true).eq(WbMessageDO::getId, messageId));
            }
        } catch (RuntimeException e) {
            if (retried < RETRY_MINUTES.length) {
                log.warn("邮件发送失败（{}），{} 分钟后重试：{}", to, RETRY_MINUTES[retried], e.getMessage());
                executor.schedule(() -> attempt(to, subject, text, messageId, retried + 1), RETRY_MINUTES[retried], TimeUnit.MINUTES);
            } else {
                log.error("邮件发送失败，已重试 {} 次（{}）：{}", RETRY_MINUTES.length, to, e.getMessage());
            }
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
