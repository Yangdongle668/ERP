package com.erp.module.sales.listener;

import com.erp.module.crm.api.customer.CustomerOwnerChangedEvent;
import com.erp.module.sales.service.order.OrderService;
import com.erp.module.sales.service.quotation.QuotationService;
import com.erp.module.sales.service.quotation.RfqService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** CRM 事件：客户转移时同步转移未完成的 RFQ、报价、订单（SAL-SO-R13） */
@Component
public class SalesCrmEventListener {

    private final OrderService orderService;
    private final QuotationService quotationService;
    private final RfqService rfqService;

    public SalesCrmEventListener(OrderService orderService, QuotationService quotationService, RfqService rfqService) {
        this.orderService = orderService;
        this.quotationService = quotationService;
        this.rfqService = rfqService;
    }

    @EventListener
    public void onOwnerChanged(CustomerOwnerChangedEvent e) {
        if (!e.isTransferDocs() || e.getCustomerIds().isEmpty() || e.getNewOwnerId() == null) return;
        rfqService.transferOwner(e.getCustomerIds(), e.getNewOwnerId());
        quotationService.transferOwner(e.getCustomerIds(), e.getNewOwnerId());
        orderService.transferOwner(e.getCustomerIds(), e.getNewOwnerId());
    }
}
