package com.erp.module.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.quality.dal.dataobject.QcInspectionDO;
import com.erp.module.quality.dal.mapper.QcInspectionMapper;
import com.erp.module.quality.service.capa.CapaService;
import com.erp.module.quality.service.complaint.ComplaintService;
import com.erp.module.quality.service.scar.ScarService;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 品质定时任务：检验超时（R08）、CAPA 到期 / 超期（R04）、客诉回复（R02）、SCAR 逾期（R02） */
@Component
public class QualityJobs {

    private final QcInspectionMapper inspectionMapper;
    private final CapaService capaService;
    private final ComplaintService complaintService;
    private final ScarService scarService;
    private final QcSupport support;

    public QualityJobs(QcInspectionMapper inspectionMapper, CapaService capaService, ComplaintService complaintService, ScarService scarService,
                       QcSupport support) {
        this.inspectionMapper = inspectionMapper;
        this.capaService = capaService;
        this.complaintService = complaintService;
        this.scarService = scarService;
        this.support = support;
    }

    /** 创建超过参数小时数仍未判定的检验单提醒品质主管（每单只提醒一次） */
    @ErpJob(code = "QC_INSPECTION_OVERDUE", name = "检验超时提醒", cron = "0 5 * * * ?")
    public String inspectionOverdue() {
        int hours = Math.max(1, support.params().getInt(QualityModuleConfig.P_OVERDUE_HOURS));
        List<QcInspectionDO> list = inspectionMapper.selectList(new LambdaQueryWrapper<QcInspectionDO>()
                .in(QcInspectionDO::getInspStatus, List.of(InspStatus.PENDING.name(), InspStatus.INSPECTING.name(), InspStatus.WAIT_MRB.name()))
                .eq(QcInspectionDO::getOverdueNotified, false).lt(QcInspectionDO::getCreatedAt, LocalDateTime.now().minusHours(hours)));
        List<Long> managers = support.managers();
        for (QcInspectionDO d : list) {
            support.alert("QC_INS_OVERDUE_" + d.getId(), AlertRaisedEvent.Level.WARNING, managers, QualityModuleConfig.INSPECTION, d.getId(),
                    "检验超时：" + d.getDocNo(), InspectType.valueOf(d.getInspectType()).label() + " 创建超过 " + hours + " 小时未判定",
                    "/quality/inspection/" + d.getId());
            d.setOverdueNotified(true);
            inspectionMapper.updateByIdOrFail(d);
        }
        return "提醒 " + list.size() + " 张";
    }

    @ErpJob(code = "QC_FOLLOWUP_REMIND", name = "CAPA / 客诉 / SCAR 期限提醒", cron = "0 30 8 * * ?")
    public String followupRemind() {
        LocalDate today = LocalDate.now();
        int capa = capaService.remind(today);
        int cpl = complaintService.remind(today);
        int scar = scarService.remind(today);
        return "CAPA " + capa + "，客诉 " + cpl + "，SCAR " + scar;
    }
}
