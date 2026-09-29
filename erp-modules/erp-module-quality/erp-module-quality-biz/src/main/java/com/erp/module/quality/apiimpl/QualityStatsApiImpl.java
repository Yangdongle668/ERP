package com.erp.module.quality.apiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.api.stats.QualityStatsApi;
import com.erp.module.quality.dal.dataobject.QcInspectionDO;
import com.erp.module.quality.dal.dataobject.QcScarDO;
import com.erp.module.quality.dal.mapper.QcInspectionMapper;
import com.erp.module.quality.service.ScarStatus;
import com.erp.module.quality.service.inspection.InspectionService;
import com.erp.module.quality.service.scar.ScarService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class QualityStatsApiImpl implements QualityStatsApi {

    private final QcInspectionMapper inspectionMapper;
    private final ScarService scarService;

    public QualityStatsApiImpl(QcInspectionMapper inspectionMapper, ScarService scarService) {
        this.inspectionMapper = inspectionMapper;
        this.scarService = scarService;
    }

    @Override
    public SupplierLotStats supplierLotStats(Long supplierId, LocalDate from, LocalDate to) {
        List<QcInspectionDO> lots = inspectionMapper.selectList(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getSupplierId, supplierId)
                .in(QcInspectionDO::getInspectType, List.of(InspectType.IQC.name(), InspectType.RECHECK.name())).isNotNull(QcInspectionDO::getResult)
                .ge(from != null, QcInspectionDO::getJudgeAt, from == null ? null : from.atStartOfDay())
                .lt(to != null, QcInspectionDO::getJudgeAt, to == null ? null : to.plusDays(1).atStartOfDay()));
        int q = count(lots, InspectionService.QUALIFIED);
        int c = count(lots, InspectionService.CONCESSION);
        int r = count(lots, InspectionService.REJECTED);
        int s = count(lots, InspectionService.SORTED);
        BigDecimal rate = lots.isEmpty() ? null : BigDecimal.valueOf(q * 100L).divide(BigDecimal.valueOf(lots.size()), 2, RoundingMode.HALF_UP);
        List<QcScarDO> scars = scarService.bySupplier(supplierId, from, to);
        int closed = (int) scars.stream().filter(x -> ScarStatus.CLOSED.name().equals(x.getScarStatus())).count();
        LocalDate today = LocalDate.now();
        int overdue = (int) scars.stream().filter(x -> x.getReplyDueDate() != null
                && (x.getRepliedAt() == null ? x.getReplyDueDate().isBefore(today) : x.getRepliedAt().toLocalDate().isAfter(x.getReplyDueDate()))).count();
        return new SupplierLotStats(supplierId, lots.size(), q, c, r, s, rate, scars.size(), closed, overdue);
    }

    private static int count(List<QcInspectionDO> lots, String result) {
        return (int) lots.stream().filter(l -> result.equals(l.getResult())).count();
    }
}
