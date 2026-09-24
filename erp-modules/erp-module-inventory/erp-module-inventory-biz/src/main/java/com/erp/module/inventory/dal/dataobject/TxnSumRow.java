package com.erp.module.inventory.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;

/** 流水汇总行（按物料 + 仓库） */
@Data
public class TxnSumRow {
    private Long materialId;
    private Long warehouseId;
    private BigDecimal qty;
    private BigDecimal amount;
}
