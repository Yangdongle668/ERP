package com.erp.module.purchase.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;

/** 退货汇总：退款、换货已出库数量 */
@Data
public class ReturnAggRow {

    private Long id;
    private BigDecimal refundQty;
    private BigDecimal replaceQty;
}
