package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 库存流水（只增不改） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_stock_txn")
public class StockTxnDO extends BaseDO {

    private String txnNo;
    private String docType;
    private String bizType;
    private Long docId;
    private Long docLineId;
    private String docNo;
    private String sourceType;
    private Long sourceId;
    private Long sourceLineId;
    private String sourceNo;
    private String direction;
    private Long materialId;
    private Long warehouseId;
    private Long locationId;
    private String batchNo;
    private BigDecimal qty;
    private BigDecimal unitCost;
    private BigDecimal amount;
    private BigDecimal balanceQty;
    private LocalDate bizDate;
    private String period;
    private Boolean isReversal;
    private Long reversedTxnId;
    private Long operatorId;
}
