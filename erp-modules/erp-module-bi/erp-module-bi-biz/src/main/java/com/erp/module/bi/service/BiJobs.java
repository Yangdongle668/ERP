package com.erp.module.bi.service;

import com.erp.module.bi.service.ai.AiAnomalyService;
import com.erp.module.bi.service.ai.AiChatService;
import com.erp.module.bi.service.ai.AiWeeklyReportService;
import com.erp.module.bi.service.etl.BiEtlService;
import com.erp.module.bi.service.etl.BiEtlService.Job;
import com.erp.module.bi.service.etl.BiEtlService.Stat;
import com.erp.module.bi.service.subscription.BiSubscriptionService;
import com.erp.module.system.api.job.ErpJob;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** BI / AI 定时任务（需求 13-01 BI-DATA-R01 / R02、13-04 第 2.2、2.3 节、AI-R07） */
@Component
public class BiJobs {

    static final List<Job> RECON = List.of(Job.RECON_SALES, Job.RECON_PURCHASE, Job.RECON_PRODUCTION, Job.RECON_QUALITY, Job.RECON_INVENTORY,
            Job.RECON_FINANCE);

    private final BiEtlService etlService;
    private final AiAnomalyService anomalyService;
    private final AiWeeklyReportService weeklyReportService;
    private final AiChatService chatService;
    private final BiSubscriptionService subscriptionService;

    public BiJobs(BiEtlService etlService, AiAnomalyService anomalyService, AiWeeklyReportService weeklyReportService, AiChatService chatService,
                  BiSubscriptionService subscriptionService) {
        this.etlService = etlService;
        this.anomalyService = anomalyService;
        this.weeklyReportService = weeklyReportService;
        this.chatService = chatService;
        this.subscriptionService = subscriptionService;
    }

    /** 增量处理：每 10 分钟重算最近区间（数据延迟 ≤ 15 分钟） */
    @ErpJob(code = "BI_INCREMENTAL", name = "BI 汇总增量更新", cron = "0 */10 * * * ?")
    public String incremental() {
        Stat s = etlService.runScheduled(Job.INCREMENTAL);
        return "处理 " + s.rows() + " 行，更新 " + s.diff() + " 行";
    }

    /** 全量校对：重算最近 3 个月，差异行覆盖并计数 */
    @ErpJob(code = "BI_NIGHTLY_RECONCILE", name = "BI 汇总全量校对", cron = "0 0 3 * * ?")
    public String reconcile() {
        List<String> parts = new ArrayList<>();
        int diff = 0;
        for (Job j : RECON) {
            Stat s = etlService.runScheduled(j);
            diff += s.diff();
            parts.add(j.label + " " + s.diff());
        }
        return "差异行 " + diff + "（" + String.join("，", parts) + "）";
    }

    /** 库存日快照 */
    @ErpJob(code = "BI_INVENTORY_SNAPSHOT", name = "BI 库存日快照", cron = "0 50 23 * * ?")
    public String snapshot() {
        return "快照 " + etlService.runScheduled(Job.INV_SNAPSHOT).rows() + " 行";
    }

    @ErpJob(code = "AI_ANOMALY_DETECT", name = "AI 异常检测与解读", cron = "0 0 8 * * ?")
    public String anomalies() {
        return "发现异常 " + anomalyService.detect(LocalDate.now()) + " 项";
    }

    @ErpJob(code = "AI_WEEKLY_REPORT", name = "AI 经营周报", cron = "0 30 7 ? * MON")
    public String weeklyReport() {
        return weeklyReportService.generate(LocalDate.now()).getTitle();
    }

    @ErpJob(code = "AI_LOG_CLEANUP", name = "清理 AI 问答日志（180 天）", cron = "0 40 2 * * ?")
    public String cleanupLogs() {
        return "删除 " + chatService.cleanupLogs() + " 条";
    }

    @ErpJob(code = "BI_SUBSCRIPTION", name = "BI 报表订阅发送", cron = "0 40 7 * * ?")
    public String subscriptions() {
        return subscriptionService.runDue(LocalDate.now());
    }
}
