package com.erp.module.quality.service.bi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.bi.api.fact.BiFactProvider;
import com.erp.module.bi.api.fact.BiFacts.QualityFact;
import com.erp.module.quality.dal.dataobject.QcComplaintDO;
import com.erp.module.quality.dal.dataobject.QcInspectionDO;
import com.erp.module.quality.dal.dataobject.QcNcrDO;
import com.erp.module.quality.dal.mapper.QcComplaintMapper;
import com.erp.module.quality.dal.mapper.QcInspectionMapper;
import com.erp.module.quality.dal.mapper.QcNcrMapper;
import com.erp.module.quality.service.InspStatus;
import com.erp.module.quality.service.inspection.InspectionService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * BI 品质事实（需求 13-01）：检验批次（判定日，合格 / 让步 / 拒收，缺陷数 = CR + MA + MI）、NCR（单据日期）、客诉（接收日期，已取消不计）。
 */
@Component
public class QualityBiFactProvider implements BiFactProvider {

    private final QcInspectionMapper inspectionMapper;
    private final QcNcrMapper ncrMapper;
    private final QcComplaintMapper complaintMapper;

    public QualityBiFactProvider(QcInspectionMapper inspectionMapper, QcNcrMapper ncrMapper, QcComplaintMapper complaintMapper) {
        this.inspectionMapper = inspectionMapper;
        this.ncrMapper = ncrMapper;
        this.complaintMapper = complaintMapper;
    }

    @Override
    public List<QualityFact> qualityFacts(LocalDate from, LocalDate to) {
        List<QualityFact> facts = new ArrayList<>();
        for (QcInspectionDO d : inspectionMapper.selectList(new LambdaQueryWrapper<QcInspectionDO>().ne(QcInspectionDO::getInspStatus, InspStatus.CANCELED.name())
                .isNotNull(QcInspectionDO::getResult).ge(QcInspectionDO::getJudgeAt, from.atStartOfDay()).lt(QcInspectionDO::getJudgeAt, to.plusDays(1).atStartOfDay()))) {
            String r = d.getResult();
            int defects = n(d.getCrCount()) + n(d.getMaCount()) + n(d.getMiCount());
            facts.add(new QualityFact(d.getJudgeAt().toLocalDate(), d.getInspectType(), d.getSupplierId(), d.getCustomerId(), d.getMaterialId(), 1,
                    InspectionService.PASS.equals(r) ? 1 : 0, InspectionService.CONCESSION.equals(r) ? 1 : 0, InspectionService.FAIL.equals(r) ? 1 : 0,
                    defects, 0, 0));
        }
        for (QcNcrDO n : ncrMapper.selectList(new LambdaQueryWrapper<QcNcrDO>().ne(QcNcrDO::getStatus, DocStatus.VOIDED).between(QcNcrDO::getDocDate, from, to))) {
            facts.add(new QualityFact(n.getDocDate(), "NCR", n.getSupplierId(), n.getCustomerId(), n.getMaterialId(), 0, 0, 0, 0, 0, 1, 0));
        }
        for (QcComplaintDO c : complaintMapper.selectList(new LambdaQueryWrapper<QcComplaintDO>().ne(QcComplaintDO::getComplaintStatus, "CANCELED")
                .ge(QcComplaintDO::getReceivedAt, from.atStartOfDay()).lt(QcComplaintDO::getReceivedAt, to.plusDays(1).atStartOfDay()))) {
            facts.add(new QualityFact(c.getReceivedAt().toLocalDate(), "COMPLAINT", null, c.getCustomerId(), c.getMaterialId(), 0, 0, 0, 0, 0, 0, 1));
        }
        return facts;
    }

    static int n(Integer v) {
        return v == null ? 0 : v;
    }
}
