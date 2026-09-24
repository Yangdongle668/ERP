package com.erp.module.inventory.api.doc;

import com.erp.common.event.DomainEvent;

/**
 * 仓库单据生命周期事件（同一事务内同步发布）：
 * <ul>
 *   <li>{@link Kind#IN_REVERSING} / {@link Kind#OUT_REVERSING}：反确认前，来源模块可抛出 BizException 阻止（如已对账、已开票）；</li>
 *   <li>{@link Kind#IN_REVERSED} / {@link Kind#OUT_REVERSED}：反确认后，来源模块扣回已入库/已出库数量；</li>
 *   <li>{@link Kind#REJECTED}：仓管员退回业务生成的单据（单据作废），来源模块提醒经办人；</li>
 *   <li>{@link Kind#RECHECK_REQUESTED}：复检送检调拨确认后，品质模块生成复检单。</li>
 * </ul>
 */
public class StockDocEvent extends DomainEvent {

    public enum Kind { IN_REVERSING, IN_REVERSED, OUT_REVERSING, OUT_REVERSED, REJECTED, RECHECK_REQUESTED }

    /** STOCK_IN / STOCK_OUT / TRANSFER */
    private final String docType;
    private final Kind kind;
    private final Long docId;
    private final String docNo;
    private final SourceRef source;
    private final String reason;

    public StockDocEvent(String docType, Kind kind, Long docId, String docNo, SourceRef source, String reason) {
        this.docType = docType;
        this.kind = kind;
        this.docId = docId;
        this.docNo = docNo;
        this.source = source;
        this.reason = reason;
    }

    public String getDocType() { return docType; }
    public Kind getKind() { return kind; }
    public Long getDocId() { return docId; }
    public String getDocNo() { return docNo; }
    public SourceRef getSource() { return source; }
    public String getReason() { return reason; }
}
