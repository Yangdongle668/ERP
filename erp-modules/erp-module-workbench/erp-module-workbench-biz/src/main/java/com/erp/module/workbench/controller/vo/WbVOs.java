package com.erp.module.workbench.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 工作台接口 VO（需求 02-工作台） */
public final class WbVOs {

    private WbVOs() {
    }

    // ==================== 首页 ====================

    /** 欢迎区计数 */
    public record Summary(long approvals, long tasks, long alerts, long unreadMessages, int pollSeconds) {
    }

    /** 当前用户可用的卡片（按布局顺序） */
    public record CardVO(String code, String name, String type, String route, boolean visible) {
    }

    public record CardDataVO(String code, BigDecimal value, String unit, BigDecimal changePct, String compareLabel, boolean costLike, String subText,
                             List<Point> series, LocalDateTime updatedAt) {
    }

    public record Point(String label, BigDecimal value) {
    }

    public record LayoutItem(@NotBlank String code, boolean visible) {
    }

    public record LayoutSave(List<LayoutItem> items) {
    }

    public record ShortcutSave(List<String> routes) {
    }

    // ==================== 待办 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TodoQuery extends PageParam {
        /** PENDING（默认）/ DONE（含 CANCELED） */
        private String status;
        /** APPROVAL / TASK */
        private String category;
        private String bizType;
        private String keyword;
        private LocalDateTime createdFrom;
        private LocalDateTime createdTo;
        private Boolean overdue;
    }

    /** taskId：审批类待办对应的审批任务 ID（批量通过时调用审批流接口） */
    public record TodoVO(Long id, String todoKey, String category, String bizType, String bizNo, Long bizId, String title, String route, String priority,
                         LocalDateTime dueTime, boolean overdue, String status, LocalDateTime createdAt, LocalDateTime doneAt, Long taskId, boolean manual,
                         String link) {
    }

    // ==================== 消息 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MessageQuery extends PageParam {
        private String type;
        private Boolean read;
    }

    public record MessageVO(Long id, String msgType, String title, String content, String route, boolean read, LocalDateTime readAt,
                            LocalDateTime createdAt) {
    }

    public record UnreadCount(long total, java.util.Map<String, Long> byType) {
    }

    // ==================== 公告 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class NoticeQuery extends PageParam {
        private String keyword;
        private String status;
    }

    public record NoticeSave(@NotBlank String title, @NotBlank String content, @NotBlank String scope, List<Long> deptIds, boolean important,
                             LocalDateTime publishAt, LocalDateTime expireAt, List<Long> fileIds) {
    }

    public record NoticeRow(Long id, String title, String scope, List<Long> deptIds, String deptNames, boolean important, LocalDateTime publishAt,
                            LocalDateTime expireAt, String status, long readCount, long targetCount, String publisherName, LocalDateTime createdAt) {
    }

    public record NoticeDetail(NoticeRow header, String content, boolean read, List<com.erp.module.system.api.file.FileInfo> files) {
    }

    /** 首页公告 / 登录弹窗 */
    public record ActiveNotice(Long id, String title, String content, boolean important, LocalDateTime publishAt, boolean read) {
    }

    public record NoticeReader(Long userId, String userName, String deptName, LocalDateTime readAt) {
    }

    // ==================== 预警 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AlertQuery extends PageParam {
        private String level;
        private String alertType;
        /** 逗号分隔，默认 OPEN */
        private String statuses;
        private LocalDateTime dateFrom;
        private LocalDateTime dateTo;
    }

    public record AlertVO(Long id, String alertKey, String alertType, String level, String bizType, Long bizId, String title, String content, String route,
                          String status, LocalDateTime firstRaisedAt, LocalDateTime lastRaisedAt, String handledByName, LocalDateTime handledAt,
                          String handleRemark) {
    }

    public record AlertStats(long critical, long warning, long info) {
    }

    public record RemarkReq(@NotNull String remark) {
    }
}
