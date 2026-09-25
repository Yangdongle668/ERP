package com.erp.module.engineering.api.ecn;

import com.erp.common.event.DomainEvent;

import java.util.List;

/** ECN 审批通过：已为每个涉及的 BOM 生成新版本（已审核、尚未成为默认） */
public class EcnApprovedEvent extends DomainEvent {

    private final Long ecnId;
    private final String ecnNo;
    private final List<BomChange> changes;

    public EcnApprovedEvent(Long ecnId, String ecnNo, List<BomChange> changes) {
        this.ecnId = ecnId;
        this.ecnNo = ecnNo;
        this.changes = List.copyOf(changes);
    }

    /** 父件的旧 BOM 版本 → 新 BOM 版本 */
    public record BomChange(Long materialId, Long oldBomId, Long newBomId) {
    }

    public Long getEcnId() { return ecnId; }
    public String getEcnNo() { return ecnNo; }
    public List<BomChange> getChanges() { return changes; }
}
