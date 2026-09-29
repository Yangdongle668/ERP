package com.erp.module.quality.api.ncr;

import com.erp.common.event.DomainEvent;
import java.math.BigDecimal;

/** NCR 审批通过（MRB 处置生效）。dispositions 如 CONCESSION=4000;RETURN=1000 */
public class NcrApprovedEvent extends DomainEvent {

    private final Long ncrId;
    private final String ncrNo;
    private final String source;
    private final Long inspectionId;
    private final Long materialId;
    private final String batchNo;
    private final Long supplierId;
    private final BigDecimal ncrQty;
    private final String severity;
    private final String dispositions;

    public NcrApprovedEvent(Long ncrId, String ncrNo, String source, Long inspectionId, Long materialId, String batchNo, Long supplierId, BigDecimal ncrQty, String severity, String dispositions) {
        this.ncrId = ncrId;
        this.ncrNo = ncrNo;
        this.source = source;
        this.inspectionId = inspectionId;
        this.materialId = materialId;
        this.batchNo = batchNo;
        this.supplierId = supplierId;
        this.ncrQty = ncrQty;
        this.severity = severity;
        this.dispositions = dispositions;
    }

    public Long getNcrId() { return ncrId; }
    public String getNcrNo() { return ncrNo; }
    public String getSource() { return source; }
    public Long getInspectionId() { return inspectionId; }
    public Long getMaterialId() { return materialId; }
    public String getBatchNo() { return batchNo; }
    public Long getSupplierId() { return supplierId; }
    public BigDecimal getNcrQty() { return ncrQty; }
    public String getSeverity() { return severity; }
    public String getDispositions() { return dispositions; }
}
