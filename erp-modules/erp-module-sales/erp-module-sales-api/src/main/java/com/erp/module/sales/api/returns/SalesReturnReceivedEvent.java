package com.erp.module.sales.api.returns;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退货入库确认（SAL-SR-R03，同一事务内发布）：handling = REFUND 时财务生成红字应收；reversed = true 表示入库反确认（扣回）。
 */
public class SalesReturnReceivedEvent extends DomainEvent {

    /** @param baseQty 本次入库数量（基本单位）；amount 本次金额（原币含税） */
    public record Line(Long returnLineId, Long orderId, Long orderLineId, Long materialId, BigDecimal baseQty, BigDecimal priceInclTax,
                       BigDecimal taxRate, BigDecimal amount) {
    }

    private final Long returnId;
    private final String returnNo;
    private final Long customerId;
    private final String handling;
    private final String currency;
    private final BigDecimal exchangeRate;
    private final Long stockInId;
    private final boolean reversed;
    private final List<Line> lines;

    public SalesReturnReceivedEvent(Long returnId, String returnNo, Long customerId, String handling, String currency, BigDecimal exchangeRate,
                                    Long stockInId, boolean reversed, List<Line> lines) {
        this.returnId = returnId;
        this.returnNo = returnNo;
        this.customerId = customerId;
        this.handling = handling;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.stockInId = stockInId;
        this.reversed = reversed;
        this.lines = List.copyOf(lines);
    }

    public Long getReturnId() { return returnId; }
    public String getReturnNo() { return returnNo; }
    public Long getCustomerId() { return customerId; }
    public String getHandling() { return handling; }
    public String getCurrency() { return currency; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public Long getStockInId() { return stockInId; }
    public boolean isReversed() { return reversed; }
    public List<Line> getLines() { return lines; }

    public BigDecimal getTotalAmount() {
        return lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
