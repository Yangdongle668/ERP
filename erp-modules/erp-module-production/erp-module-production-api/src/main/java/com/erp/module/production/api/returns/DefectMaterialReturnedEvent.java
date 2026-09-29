package com.erp.module.production.api.returns;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.util.List;

/** 不良退料入库确认（品质可据此开 NCR，追溯供应商批次） */
public class DefectMaterialReturnedEvent extends DomainEvent {
    /** @param qty 基本单位数量 */
    public record Line(Long materialId, String batchNo, BigDecimal qty, String defectDesc) {
    }

    private final Long returnId;
    private final String returnNo;
    private final Long prodOrderId;
    private final String prodOrderNo;
    private final List<Line> lines;

    public DefectMaterialReturnedEvent(Long returnId, String returnNo, Long prodOrderId, String prodOrderNo, List<Line> lines) {
        this.returnId = returnId;
        this.returnNo = returnNo;
        this.prodOrderId = prodOrderId;
        this.prodOrderNo = prodOrderNo;
        this.lines = List.copyOf(lines);
    }

    public Long getReturnId() { return returnId; }
    public String getReturnNo() { return returnNo; }
    public Long getProdOrderId() { return prodOrderId; }
    public String getProdOrderNo() { return prodOrderNo; }
    public List<Line> getLines() { return lines; }
}
