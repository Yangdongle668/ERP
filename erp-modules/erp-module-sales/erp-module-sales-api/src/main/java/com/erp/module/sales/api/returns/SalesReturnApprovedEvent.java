package com.erp.module.sales.api.returns;

import com.erp.common.event.DomainEvent;

/** 销售退货审核通过（已生成退货入库单） */
public class SalesReturnApprovedEvent extends DomainEvent {

    private final Long returnId;
    private final String returnNo;
    private final Long customerId;
    private final String handling;

    public SalesReturnApprovedEvent(Long returnId, String returnNo, Long customerId, String handling) {
        this.returnId = returnId;
        this.returnNo = returnNo;
        this.customerId = customerId;
        this.handling = handling;
    }

    public Long getReturnId() { return returnId; }
    public String getReturnNo() { return returnNo; }
    public Long getCustomerId() { return customerId; }
    public String getHandling() { return handling; }
}
