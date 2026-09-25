package com.erp.module.engineering.api.ecn;

import com.erp.common.event.DomainEvent;

import java.util.List;

/**
 * ECN 生效：新 BOM 版本已成为默认。updateWipDocNos 为影响分析中处理方式为“更新用料”的在制生产订单，
 * 生产模块据此更新未领料部分的用料。
 */
public class EcnEffectiveEvent extends DomainEvent {

    private final Long ecnId;
    private final String ecnNo;
    private final List<EcnApprovedEvent.BomChange> changes;
    private final List<String> updateWipDocNos;

    public EcnEffectiveEvent(Long ecnId, String ecnNo, List<EcnApprovedEvent.BomChange> changes, List<String> updateWipDocNos) {
        this.ecnId = ecnId;
        this.ecnNo = ecnNo;
        this.changes = List.copyOf(changes);
        this.updateWipDocNos = List.copyOf(updateWipDocNos);
    }

    public Long getEcnId() { return ecnId; }
    public String getEcnNo() { return ecnNo; }
    public List<EcnApprovedEvent.BomChange> getChanges() { return changes; }
    public List<String> getUpdateWipDocNos() { return updateWipDocNos; }
}
