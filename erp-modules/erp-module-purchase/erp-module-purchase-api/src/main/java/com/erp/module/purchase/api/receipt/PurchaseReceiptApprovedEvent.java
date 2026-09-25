package com.erp.module.purchase.api.receipt;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.util.List;

/** 到货单审核后发布（同一事务内）；反审核时 approved = false。 */
public class PurchaseReceiptApprovedEvent extends DomainEvent {

    /** @param baseQty 到货数量（基本单位） */
    public record Line(Long receiptLineId, Long orderId, Long orderLineId, Long materialId, BigDecimal baseQty, boolean inspectRequired) {
    }

    private final Long receiptId;
    private final String receiptNo;
    private final String receiptType;
    private final Long supplierId;
    private final boolean approved;
    private final List<Line> lines;

    public PurchaseReceiptApprovedEvent(Long receiptId, String receiptNo, String receiptType, Long supplierId, boolean approved, List<Line> lines) {
        this.receiptId = receiptId;
        this.receiptNo = receiptNo;
        this.receiptType = receiptType;
        this.supplierId = supplierId;
        this.approved = approved;
        this.lines = List.copyOf(lines);
    }

    public Long getReceiptId() { return receiptId; }
    public String getReceiptNo() { return receiptNo; }
    public String getReceiptType() { return receiptType; }
    public Long getSupplierId() { return supplierId; }
    public boolean isApproved() { return approved; }
    public List<Line> getLines() { return lines; }
}
