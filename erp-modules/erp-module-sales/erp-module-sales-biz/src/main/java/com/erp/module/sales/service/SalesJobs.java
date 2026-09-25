package com.erp.module.sales.service;

import com.erp.module.sales.service.order.OrderExecService;
import com.erp.module.sales.service.order.PaymentPlanService;
import com.erp.module.sales.service.quotation.QuotationService;
import com.erp.module.sales.service.quotation.RfqService;
import com.erp.module.system.api.job.ErpJob;
import org.springframework.stereotype.Component;

/** 销售定时任务（由系统管理按 Cron 调度） */
@Component
public class SalesJobs {

    private final QuotationService quotationService;
    private final RfqService rfqService;
    private final PaymentPlanService paymentPlanService;
    private final OrderExecService orderExecService;

    public SalesJobs(QuotationService quotationService, RfqService rfqService, PaymentPlanService paymentPlanService, OrderExecService orderExecService) {
        this.quotationService = quotationService;
        this.rfqService = rfqService;
        this.paymentPlanService = paymentPlanService;
        this.orderExecService = orderExecService;
    }

    /** SAL-QT-R04：每天 00:10 报价过期 */
    @ErpJob(code = "SAL_QUOTATION_EXPIRE", name = "报价单过期", cron = "0 10 0 * * ?")
    public String expireQuotations() {
        return "过期 " + quotationService.expire() + " 张";
    }

    /** SAL-PP 第 3 节：每天 00:20 重算回款计划状态 */
    @ErpJob(code = "SAL_PAYMENT_STATUS", name = "回款计划状态重算", cron = "0 20 0 * * ?")
    public String refreshPaymentStatus() {
        return "更新 " + paymentPlanService.refreshStatuses() + " 条";
    }

    /** SAL-PP-R03：每周一 09:00 逾期回款提醒 */
    @ErpJob(code = "SAL_PAYMENT_OVERDUE_REMIND", name = "逾期回款提醒", cron = "0 0 9 ? * MON")
    public String remindOverduePayments() {
        return "提醒 " + paymentPlanService.remindOverdue() + " 位业务员";
    }

    /** SAL-SO-R12：每天 08:30 订单交期提醒 */
    @ErpJob(code = "SAL_DELIVERY_REMIND", name = "订单交期提醒", cron = "0 30 8 * * ?")
    public String remindDelivery() {
        return "提醒 " + orderExecService.remindDelivery() + " 位业务员";
    }

    /** SAL-RFQ-R02：每天 08:00 RFQ 回复截止提醒 */
    @ErpJob(code = "SAL_RFQ_DUE_REMIND", name = "RFQ 回复截止提醒", cron = "0 0 8 * * ?")
    public String remindRfqDue() {
        return "提醒 " + rfqService.remindDue() + " 张";
    }
}
