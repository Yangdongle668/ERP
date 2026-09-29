package com.erp.it.production;

import com.erp.module.production.api.order.ProductionOrderCompletedEvent;
import com.erp.module.production.api.report.IpqcTriggerEvent;
import com.erp.module.production.api.report.WorkReportApprovedEvent;
import com.erp.module.production.api.returns.DefectMaterialReturnedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 测试用的“品质 / 财务 / 研发工程”：接收生产事件 */
@Configuration
class ItProductionConfig {

    static final List<WorkReportApprovedEvent> REPORTS = new CopyOnWriteArrayList<>();
    static final List<IpqcTriggerEvent> IPQC = new CopyOnWriteArrayList<>();
    static final List<DefectMaterialReturnedEvent> DEFECT_RETURNS = new CopyOnWriteArrayList<>();
    static final List<ProductionOrderCompletedEvent> COMPLETED = new CopyOnWriteArrayList<>();

    @EventListener
    public void onReport(WorkReportApprovedEvent e) {
        REPORTS.add(e);
    }

    @EventListener
    public void onIpqc(IpqcTriggerEvent e) {
        IPQC.add(e);
    }

    @EventListener
    public void onDefectReturn(DefectMaterialReturnedEvent e) {
        DEFECT_RETURNS.add(e);
    }

    @EventListener
    public void onCompleted(ProductionOrderCompletedEvent e) {
        COMPLETED.add(e);
    }
}
