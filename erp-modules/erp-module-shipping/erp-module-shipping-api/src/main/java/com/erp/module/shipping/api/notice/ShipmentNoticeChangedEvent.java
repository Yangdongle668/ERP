package com.erp.module.shipping.api.notice;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.util.List;

/** 出货通知已通知数量变化（保存、关闭、按实拣完成）。lines：订单行 → 本次变化的基本单位数量（可为负） */
public class ShipmentNoticeChangedEvent extends DomainEvent {
    public record Line(Long orderLineId, BigDecimal deltaBaseQty) {
    }

    private final Long noticeId;
    private final String noticeNo;
    private final Long customerId;
    private final String noticeStatus;
    private final List<Line> lines;

    public ShipmentNoticeChangedEvent(Long noticeId, String noticeNo, Long customerId, String noticeStatus, List<Line> lines) {
        this.noticeId = noticeId;
        this.noticeNo = noticeNo;
        this.customerId = customerId;
        this.noticeStatus = noticeStatus;
        this.lines = List.copyOf(lines);
    }

    public Long getNoticeId() { return noticeId; }
    public String getNoticeNo() { return noticeNo; }
    public Long getCustomerId() { return customerId; }
    public String getNoticeStatus() { return noticeStatus; }
    public List<Line> getLines() { return lines; }
}
