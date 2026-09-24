package com.erp.module.inventory.api.doc;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.util.List;

/**
 * 入库单确认过账后发布（同一事务内，监听方可同步回写来源单据）。
 * 监听方必须幂等（以 stockInId + sourceLineId 判断）。
 */
public class StockInConfirmedEvent extends DomainEvent {

    private final Long stockInId;
    private final String stockInNo;
    private final StockInType inType;
    private final SourceRef source;
    private final Long warehouseId;
    private final String warehouseType;
    private final List<Line> lines;

    public StockInConfirmedEvent(Long stockInId, String stockInNo, StockInType inType, SourceRef source,
                                 Long warehouseId, String warehouseType, List<Line> lines) {
        this.stockInId = stockInId;
        this.stockInNo = stockInNo;
        this.inType = inType;
        this.source = source;
        this.warehouseId = warehouseId;
        this.warehouseType = warehouseType;
        this.lines = List.copyOf(lines);
    }

    /** @param baseQty 基本单位数量 */
    public record Line(Long sourceLineId, Long materialId, String batchNo, BigDecimal baseQty) {
    }

    public Long getStockInId() { return stockInId; }
    public String getStockInNo() { return stockInNo; }
    public StockInType getInType() { return inType; }
    public SourceRef getSource() { return source; }
    public Long getWarehouseId() { return warehouseId; }
    public String getWarehouseType() { return warehouseType; }
    public List<Line> getLines() { return lines; }
}
