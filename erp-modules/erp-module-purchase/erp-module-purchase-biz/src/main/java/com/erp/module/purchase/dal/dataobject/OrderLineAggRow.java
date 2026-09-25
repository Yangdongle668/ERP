package com.erp.module.purchase.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单行（或委外单）的到货汇总：已审核到货单的到货、入库、合格、已对账数量与首次到货时间 */
@Data
public class OrderLineAggRow {

    private Long id;
    private BigDecimal receivedQty;
    private BigDecimal stockedQty;
    private BigDecimal qualifiedQty;
    private BigDecimal statementQty;
    private LocalDateTime firstArrival;
}
