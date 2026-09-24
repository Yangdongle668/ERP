package com.erp.module.inventory.dal.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

/** inv_serial_txn 的一行 */
@Data
public class SerialTxnRow {
    private Long id;
    private Long serialId;
    private Long txnId;
    private String direction;
    private String docNo;
    private LocalDateTime createdAt;
}
