package com.erp.module.purchase.api.receipt;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购退货出库确认后发布（同一事务内）；出库反确认时 completed = false。
 *
 * @param handling REFUND 退货退款 / REPLACE 退货换货
 */
public class PurchaseReturnCompletedEvent extends DomainEvent {

    public record Line(Long returnLineId, Long receiptLineId, Long orderLineId, Long materialId, String batchNo, BigDecimal qty) {
    }

    private final Long returnId;
    private final String returnNo;
    private final Long supplierId;
    private final String handling;
    private final boolean completed;
    private final List<Line> lines;

    public PurchaseReturnCompletedEvent(Long returnId, String returnNo, Long supplierId, String handling, boolean completed, List<Line> lines) {
        this.returnId = returnId;
        this.returnNo = returnNo;
        this.supplierId = supplierId;
        this.handling = handling;
        this.completed = completed;
        this.lines = List.copyOf(lines);
    }

    public Long getReturnId() { return returnId; }
    public String getReturnNo() { return returnNo; }
    public Long getSupplierId() { return supplierId; }
    public String getHandling() { return handling; }
    public boolean isCompleted() { return completed; }
    public List<Line> getLines() { return lines; }
}
