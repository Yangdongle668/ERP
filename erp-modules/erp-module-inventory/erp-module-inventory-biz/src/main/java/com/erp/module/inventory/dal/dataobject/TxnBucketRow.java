package com.erp.module.inventory.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;

/** 流水按类型汇总行（收发存） */
@Data
public class TxnBucketRow {
    private Long materialId;
    private Long warehouseId;
    private String docType;
    private String bizType;
    private String direction;
    private Boolean isReversal;
    private BigDecimal qty;
    private BigDecimal amount;
    private Long amountCount;
    private Long txnCount;
}
