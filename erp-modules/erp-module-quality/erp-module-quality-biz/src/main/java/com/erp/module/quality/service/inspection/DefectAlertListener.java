package com.erp.module.quality.service.inspection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.api.inspection.InspectionJudgedEvent;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.quality.dal.dataobject.QcInspectionDO;
import com.erp.module.quality.dal.dataobject.QcInspectionDefectDO;
import com.erp.module.quality.dal.mapper.QcInspectionDefectMapper;
import com.erp.module.quality.dal.mapper.QcInspectionMapper;
import com.erp.module.quality.service.QcSupport;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同一不良当日预警（参数 qc.defect.alert-threshold）：IPQC 判定后，按缺陷代码汇总当天已判定 IPQC 的缺陷数，
 * 本次判定使累计数达到阈值时向品质主管发预警（每个缺陷代码每天一次）。
 */
@Component
public class DefectAlertListener {

    private final QcInspectionMapper inspectionMapper;
    private final QcInspectionDefectMapper defectMapper;
    private final QcSupport support;

    public DefectAlertListener(QcInspectionMapper inspectionMapper, QcInspectionDefectMapper defectMapper, QcSupport support) {
        this.inspectionMapper = inspectionMapper;
        this.defectMapper = defectMapper;
        this.support = support;
    }

    @EventListener
    public void onJudged(InspectionJudgedEvent e) {
        if (!InspectType.IPQC.name().equals(e.getInspectType())) return;
        Map<String, Integer> current = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (QcInspectionDefectDO d : defectMapper.selectByParent(e.getInspectionId())) {
            if (d.getDefectCode() == null || d.getQty() == null || d.getQty() <= 0) continue;
            current.merge(d.getDefectCode(), d.getQty(), Integer::sum);
            names.putIfAbsent(d.getDefectCode(), d.getDefectName());
        }
        if (current.isEmpty()) return;
        int threshold = support.params().getInt(QualityModuleConfig.P_DEFECT_ALERT);
        if (threshold <= 0) return;
        LocalDate today = LocalDate.now();
        Map<String, Integer> totals = DataScopes.ignore(() -> totals(today, current.keySet()));
        List<Long> managers = null;
        for (Map.Entry<String, Integer> c : current.entrySet()) {
            int total = totals.getOrDefault(c.getKey(), c.getValue());
            if (total < threshold || total - c.getValue() >= threshold) continue;
            if (managers == null) managers = support.managers();
            String name = names.get(c.getKey()) == null ? c.getKey() : c.getKey() + " " + names.get(c.getKey());
            support.alert("QC_DEFECT_" + c.getKey() + "_" + today, AlertRaisedEvent.Level.WARNING, managers, QualityModuleConfig.INSPECTION,
                    e.getInspectionId(), "同一不良超出预警：" + name, "今日制程检验不良「" + name + "」累计 " + total + " 件，达到预警阈值 " + threshold
                            + " 件（最近一单 " + e.getInspectionNo() + "）", "/quality/inspection/" + e.getInspectionId());
        }
    }

    private Map<String, Integer> totals(LocalDate day, Collection<String> codes) {
        List<Long> ids = inspectionMapper.selectList(new LambdaQueryWrapper<QcInspectionDO>()
                        .select(QcInspectionDO::getId)
                        .eq(QcInspectionDO::getInspectType, InspectType.IPQC.name())
                        .ge(QcInspectionDO::getJudgeAt, day.atStartOfDay())
                        .lt(QcInspectionDO::getJudgeAt, day.plusDays(1).atStartOfDay()))
                .stream().map(QcInspectionDO::getId).toList();
        Map<String, Integer> out = new LinkedHashMap<>();
        if (ids.isEmpty()) return out;
        for (QcInspectionDefectDO d : defectMapper.selectList(new LambdaQueryWrapper<QcInspectionDefectDO>()
                .in(QcInspectionDefectDO::getInspectionId, ids).in(QcInspectionDefectDO::getDefectCode, codes))) {
            if (d.getQty() != null && d.getQty() > 0) out.merge(d.getDefectCode(), d.getQty(), Integer::sum);
        }
        return out;
    }
}
