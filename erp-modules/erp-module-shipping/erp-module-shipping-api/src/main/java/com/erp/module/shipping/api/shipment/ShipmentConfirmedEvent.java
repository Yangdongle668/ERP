package com.erp.module.shipping.api.shipment;

import com.erp.common.event.DomainEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 出货确认（仓库销售出库确认后，同一事务内）：销售回写已由出货模块调用，财务据此生成应收。金额为原币含税 */
public class ShipmentConfirmedEvent extends DomainEvent {
    /** @param baseQty 基本单位；amount 原币含税 */
    public record Line(Long shipmentLineId, Long orderId, Long orderLineId, Long materialId, String batchNo, BigDecimal baseQty, BigDecimal priceInclTax,
                       BigDecimal taxRate, BigDecimal amount) {
    }

    private final Long shipmentId;
    private final String shipmentNo;
    private final Long customerId;
    private final String currency;
    private final BigDecimal exchangeRate;
    private final LocalDate shipDate;
    private final BigDecimal totalAmount;
    private final BigDecimal totalAmountBase;
    private final List<Line> lines;

    public ShipmentConfirmedEvent(Long shipmentId, String shipmentNo, Long customerId, String currency, BigDecimal exchangeRate, LocalDate shipDate, BigDecimal totalAmount, BigDecimal totalAmountBase, List<Line> lines) {
        this.shipmentId = shipmentId;
        this.shipmentNo = shipmentNo;
        this.customerId = customerId;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.shipDate = shipDate;
        this.totalAmount = totalAmount;
        this.totalAmountBase = totalAmountBase;
        this.lines = List.copyOf(lines);
    }

    public Long getShipmentId() { return shipmentId; }
    public String getShipmentNo() { return shipmentNo; }
    public Long getCustomerId() { return customerId; }
    public String getCurrency() { return currency; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public LocalDate getShipDate() { return shipDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getTotalAmountBase() { return totalAmountBase; }
    public List<Line> getLines() { return lines; }
}
