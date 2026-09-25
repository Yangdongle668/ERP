package com.erp.module.purchase.service;

import com.erp.module.purchase.service.order.OrderService;
import com.erp.module.purchase.service.price.PriceService;
import com.erp.module.purchase.service.score.ScoreService;
import com.erp.module.purchase.service.supplier.SupplierService;
import com.erp.module.system.api.job.ErpJob;
import org.springframework.stereotype.Component;

/** 资材定时任务（由系统管理按 Cron 调度） */
@Component
public class PurchaseJobs {

    private final PriceService priceService;
    private final SupplierService supplierService;
    private final OrderService orderService;
    private final ScoreService scoreService;

    public PurchaseJobs(PriceService priceService, SupplierService supplierService, OrderService orderService, ScoreService scoreService) {
        this.priceService = priceService;
        this.supplierService = supplierService;
        this.orderService = orderService;
        this.scoreService = scoreService;
    }

    /** PUR-PRC-R03：每天 00:05 价格过期与新价格替代 */
    @ErpJob(code = "PUR_PRICE_DAILY", name = "采购价格生效与过期", cron = "0 5 0 * * ?")
    public String refreshPrices() {
        return priceService.refreshDaily();
    }

    /** PUR-SUP-R05：资质到期前 30 天、到期当天提醒 */
    @ErpJob(code = "PUR_CERT_EXPIRY", name = "供应商资质到期提醒", cron = "0 30 7 * * ?")
    public String remindCertExpiry() {
        return "提醒 " + supplierService.remindCertExpiry() + " 条";
    }

    /** PUR-PO-R10：每天 08:00 交期提醒 */
    @ErpJob(code = "PUR_DELIVERY_REMIND", name = "采购交期与逾期提醒", cron = "0 0 8 * * ?")
    public String remindOverdue() {
        return "提醒 " + orderService.remindOverdue() + " 位采购员";
    }

    /** PUR-SC-R04：每月 3 日 02:00 自动计算上月评估 */
    @ErpJob(code = "PUR_SCORE_MONTHLY", name = "供应商月度评估计算", cron = "0 0 2 3 * ?")
    public String calculateScores() {
        return scoreService.monthlyJob();
    }
}
