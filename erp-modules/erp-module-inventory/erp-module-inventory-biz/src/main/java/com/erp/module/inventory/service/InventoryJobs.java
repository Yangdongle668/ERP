package com.erp.module.inventory.service;

import com.erp.module.inventory.controller.vo.ReportVOs.AlertRow;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.notify.NotifyApi;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 仓库定时任务（需求 08-08 2.6、08-07）：库存预警每小时计算一次，临期/过期批次每天提醒；
 * 预警发到工作台（同一物料同一预警类型未处理前按 alertKey 更新，不重复新增）。
 */
@Component
public class InventoryJobs {

    static final String PERMISSION = "inv:stock:query";

    private final StockReportService reportService;
    private final NotifyApi notifyApi;

    public InventoryJobs(StockReportService reportService, NotifyApi notifyApi) {
        this.reportService = reportService;
        this.notifyApi = notifyApi;
    }

    @ErpJob(code = "INV_STOCK_ALERT", name = "库存预警检查", cron = "0 5 * * * ?")
    public String stockAlerts() {
        List<AlertRow> low = reportService.stockLevelAlerts(true);
        List<AlertRow> high = reportService.stockLevelAlerts(false);
        List<AlertRow> qc = reportService.qcOverdueAlerts();
        low.forEach(r -> raise("INV_LOW:" + r.materialId(), "INV_STOCK_LOW", AlertRaisedEvent.Level.WARNING, r,
                "低于安全库存：" + r.materialCode(), "物料 " + r.materialCode() + " " + r.materialName() + " 可用量 " + plain(r.availableQty())
                        + "，安全库存 " + plain(r.safetyStock()) + "，缺口 " + plain(r.gap()), "LOW"));
        high.forEach(r -> raise("INV_HIGH:" + r.materialId(), "INV_STOCK_HIGH", AlertRaisedEvent.Level.INFO, r,
                "超过最高库存：" + r.materialCode(), "物料 " + r.materialCode() + " 现存 " + plain(r.qty()) + "，最高库存 " + plain(r.maxStock()), "HIGH"));
        qc.forEach(r -> raise("INV_QC:" + r.materialId() + ":" + r.warehouseId() + ":" + r.batchNo(), "INV_QC_OVERDUE", AlertRaisedEvent.Level.WARNING, r,
                "待检超时：" + r.materialCode(), "物料 " + r.materialCode() + (r.batchNo() == null ? "" : " 批次 " + r.batchNo()) + " 在待检仓已等待 "
                        + r.waitHours() + " 小时", "QC_OVERDUE"));
        return "低于安全库存 " + low.size() + " 项，超过最高库存 " + high.size() + " 项，待检超时 " + qc.size() + " 项";
    }

    @ErpJob(code = "INV_EXPIRY_ALERT", name = "批次临期/过期提醒", cron = "0 30 7 * * ?")
    public String expiryAlerts() {
        List<AlertRow> rows = reportService.expiryAlerts();
        rows.forEach(r -> raise("INV_EXPIRY:" + r.materialId() + ":" + r.batchNo(), "INV_BATCH_EXPIRY",
                r.daysLeft() != null && r.daysLeft() < 0 ? AlertRaisedEvent.Level.CRITICAL : AlertRaisedEvent.Level.WARNING, r,
                (r.daysLeft() != null && r.daysLeft() < 0 ? "批次已过期：" : "批次临期：") + r.materialCode() + " " + r.batchNo(),
                "物料 " + r.materialCode() + " 批次 " + r.batchNo() + " 到期日 " + r.expireDate() + "，库存 " + plain(r.qty()) + "（" + r.warehouseName() + "）",
                "EXPIRY"));
        return "临期/过期批次 " + rows.size() + " 项";
    }

    private void raise(String key, String type, AlertRaisedEvent.Level level, AlertRow r, String title, String content, String tab) {
        notifyApi.alert(new AlertRaisedEvent(key, type, level, List.of(), PERMISSION, "INV_MATERIAL", r.materialId(), title, content,
                "/inventory/stock?tab=alert&type=" + tab));
    }

    private static String plain(java.math.BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
