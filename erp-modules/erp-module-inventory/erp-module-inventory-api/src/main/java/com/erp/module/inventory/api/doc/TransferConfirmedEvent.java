package com.erp.module.inventory.api.doc;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.util.List;

/** 调拨单确认后发布（同一事务内）；检验调拨时品质据此更新检验单状态 */
public class TransferConfirmedEvent extends DomainEvent {

    public record Line(Long sourceLineId, Long materialId, String batchNo, BigDecimal qty, JudgeResult judgeResult) {
    }

    private final Long transferId;
    private final String transferNo;
    private final TransferType transferType;
    private final SourceRef source;
    private final Long inspectionId;
    private final Long fromWarehouseId;
    private final Long toWarehouseId;
    private final List<Line> lines;

    public TransferConfirmedEvent(Long transferId, String transferNo, TransferType transferType, SourceRef source, Long inspectionId,
                                  Long fromWarehouseId, Long toWarehouseId, List<Line> lines) {
        this.transferId = transferId;
        this.transferNo = transferNo;
        this.transferType = transferType;
        this.source = source;
        this.inspectionId = inspectionId;
        this.fromWarehouseId = fromWarehouseId;
        this.toWarehouseId = toWarehouseId;
        this.lines = List.copyOf(lines);
    }

    public Long getTransferId() { return transferId; }
    public String getTransferNo() { return transferNo; }
    public TransferType getTransferType() { return transferType; }
    public SourceRef getSource() { return source; }
    public Long getInspectionId() { return inspectionId; }
    public Long getFromWarehouseId() { return fromWarehouseId; }
    public Long getToWarehouseId() { return toWarehouseId; }
    public List<Line> getLines() { return lines; }
}
