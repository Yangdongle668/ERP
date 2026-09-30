package com.erp.module.production.service.bi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.bi.api.fact.BiFactProvider;
import com.erp.module.bi.api.fact.BiFacts.ProductionFact;
import com.erp.module.production.dal.dataobject.MfgFinishDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgReportDO;
import com.erp.module.production.dal.mapper.MfgFinishMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgReportMapper;
import com.erp.module.production.service.ProdStatus;
import com.erp.module.production.service.report.ReportService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BI 生产事实（需求 13-01）：报工（已审核，报工日期：合格 / 不良 / 报废 / 工时，正常报工（非维修、报废报工）的合格数计一次合格）、
 * 合格完工入库数量（完工单日期）、计划数量与延期订单（计划完工日；到期未完工或实际完工晚于计划为延期）。车间取生产订单部门。
 */
@Component
public class ProductionBiFactProvider implements BiFactProvider {

    static final Set<String> NOT_PLANNED = Set.of(ProdStatus.DRAFT.name(), ProdStatus.PENDING.name(), ProdStatus.VOIDED.name());

    private final MfgProdOrderMapper orderMapper;
    private final MfgReportMapper reportMapper;
    private final MfgFinishMapper finishMapper;

    public ProductionBiFactProvider(MfgProdOrderMapper orderMapper, MfgReportMapper reportMapper, MfgFinishMapper finishMapper) {
        this.orderMapper = orderMapper;
        this.reportMapper = reportMapper;
        this.finishMapper = finishMapper;
    }

    @Override
    public List<ProductionFact> productionFacts(LocalDate from, LocalDate to) {
        List<ProductionFact> facts = new ArrayList<>();
        List<MfgReportDO> reports = reportMapper.selectList(new LambdaQueryWrapper<MfgReportDO>().eq(MfgReportDO::getStatus, DocStatus.APPROVED)
                .between(MfgReportDO::getReportDate, from, to));
        List<MfgFinishDO> finishes = finishMapper.selectList(new LambdaQueryWrapper<MfgFinishDO>().ne(MfgFinishDO::getStatus, DocStatus.VOIDED)
                .between(MfgFinishDO::getDocDate, from, to));
        List<MfgProdOrderDO> planned = orderMapper.selectList(new LambdaQueryWrapper<MfgProdOrderDO>().between(MfgProdOrderDO::getPlanEnd, from, to)
                .notIn(MfgProdOrderDO::getProdStatus, NOT_PLANNED));
        Set<Long> orderIds = new HashSet<>();
        reports.forEach(r -> orderIds.add(r.getProdOrderId()));
        finishes.forEach(f -> orderIds.add(f.getProdOrderId()));
        Map<Long, MfgProdOrderDO> orders = orderIds.isEmpty() ? Map.of() : orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(MfgProdOrderDO::getId, Function.identity()));
        for (MfgReportDO r : reports) {
            MfgProdOrderDO o = orders.get(r.getProdOrderId());
            if (o == null) continue;
            BigDecimal good = nz(r.getGoodQty());
            boolean rework = !ReportService.NORMAL.equals(r.getReportKind());
            facts.add(new ProductionFact(r.getReportDate(), r.getDeptId() != null ? r.getDeptId() : o.getDeptId(), o.getMaterialId(), BigDecimal.ZERO, good,
                    nz(r.getDefectQty()), nz(r.getScrapQty()), nz(r.getWorkHours()), BigDecimal.ZERO, BigDecimal.ZERO, rework ? BigDecimal.ZERO : good, 0));
        }
        for (MfgFinishDO f : finishes) {
            MfgProdOrderDO o = orders.get(f.getProdOrderId());
            BigDecimal qty = f.getQualifiedQty() != null && f.getQualifiedQty().signum() > 0 ? f.getQualifiedQty() : BigDecimal.ZERO;
            facts.add(new ProductionFact(f.getDocDate(), f.getDeptId() != null ? f.getDeptId() : o == null ? null : o.getDeptId(), f.getMaterialId(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, qty, BigDecimal.ZERO, 0));
        }
        LocalDate today = LocalDate.now();
        for (MfgProdOrderDO o : planned) {
            boolean done = ProdStatus.COMPLETED.name().equals(o.getProdStatus()) || ProdStatus.CLOSED.name().equals(o.getProdStatus());
            boolean delayed = done ? o.getActualEnd() != null && o.getActualEnd().toLocalDate().isAfter(o.getPlanEnd()) : o.getPlanEnd().isBefore(today);
            facts.add(new ProductionFact(o.getPlanEnd(), o.getDeptId(), o.getMaterialId(), nz(o.getQty()), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, delayed ? 1 : 0));
        }
        return facts;
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
