package com.erp.module.system.api.paymentterm;

/** 付款节点起算事件：下单日 / 出货前 / 出货日 / 提单日 / 开票日 / 到货（入库）日 / 月结（事件当月月末） */
public enum BaseEvent {
    ORDER_DATE, BEFORE_SHIPMENT, SHIPMENT, BL_DATE, INVOICE_DATE, RECEIPT_DATE, MONTH_END
}
