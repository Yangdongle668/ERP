package com.erp.module.inventory.api.doc;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.util.List;

/** 出库单确认过账后发布（同一事务内）。一个来源行可能对应多个批次行。 */
public class StockOutConfirmedEvent extends DomainEvent {

    private final Long stockOutId;
    private final String stockOutNo;
    private final StockOutType outType;
    private final SourceRef source;
    private final Long warehouseId;
    private final List<Line> lines;

    public StockOutConfirmedEvent(Long stockOutId, String stockOutNo, StockOutType outType, SourceRef source,
                                  Long warehouseId, List<Line> lines) {
        this.stockOutId = stockOutId;
        this.stockOutNo = stockOutNo;
        this.outType = outType;
        this.source = source;
        this.warehouseId = warehouseId;
        this.lines = List.copyOf(lines);
    }

    public record Line(Long sourceLineId, Long materialId, String batchNo, BigDecimal baseQty) {
    }

    public Long getStockOutId() { return stockOutId; }
    public String getStockOutNo() { return stockOutNo; }
    public StockOutType getOutType() { return outType; }
    public SourceRef getSource() { return source; }
    public Long getWarehouseId() { return warehouseId; }
    public List<Line> getLines() { return lines; }
}
