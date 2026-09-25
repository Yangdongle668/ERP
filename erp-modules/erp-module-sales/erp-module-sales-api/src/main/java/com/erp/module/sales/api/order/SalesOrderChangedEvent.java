package com.erp.module.sales.api.order;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 订单变更审核生效（SAL-SC-R05）：PMC 更新需求并评估已有生产订单（数量减少、取消的行） */
public class SalesOrderChangedEvent extends DomainEvent {

    /**
     * @param changeType ADD / MODIFY / CANCEL
     */
    public record LineChange(Long lineId, Long materialId, String changeType, BigDecimal oldBaseQty, BigDecimal newBaseQty,
                             LocalDate oldRequiredDate, LocalDate newRequiredDate) {

        public boolean decreased() {
            return "CANCEL".equals(changeType) || oldBaseQty != null && newBaseQty != null && newBaseQty.compareTo(oldBaseQty) < 0;
        }
    }

    private final Long orderId;
    private final String orderNo;
    private final int orderVersion;
    private final Long changeId;
    private final String changeNo;
    private final List<LineChange> changes;
    /** 变更后的全部行快照 */
    private final List<SalesOrderLineInfo> lines;

    public SalesOrderChangedEvent(Long orderId, String orderNo, int orderVersion, Long changeId, String changeNo, List<LineChange> changes,
                                  List<SalesOrderLineInfo> lines) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.orderVersion = orderVersion;
        this.changeId = changeId;
        this.changeNo = changeNo;
        this.changes = List.copyOf(changes);
        this.lines = List.copyOf(lines);
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNo() { return orderNo; }
    public int getOrderVersion() { return orderVersion; }
    public Long getChangeId() { return changeId; }
    public String getChangeNo() { return changeNo; }
    public List<LineChange> getChanges() { return changes; }
    public List<SalesOrderLineInfo> getLines() { return lines; }
}
