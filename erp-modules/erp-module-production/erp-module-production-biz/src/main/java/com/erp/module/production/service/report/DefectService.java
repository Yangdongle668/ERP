package com.erp.module.production.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.production.api.ProductionErrorCodes;
import com.erp.module.production.api.defect.DefectNcrCreator;
import com.erp.module.production.controller.vo.ReportVOs.DefectQuery;
import com.erp.module.production.controller.vo.ReportVOs.DefectRow;
import com.erp.module.production.controller.vo.ReportVOs.ParetoItem;
import com.erp.module.production.controller.vo.ReportVOs.TrendPoint;
import com.erp.module.production.controller.vo.ReportVOs.YieldQuery;
import com.erp.module.production.controller.vo.ReportVOs.YieldReport;
import com.erp.module.production.controller.vo.ReportVOs.YieldRow;
import com.erp.module.production.dal.dataobject.MfgDefectDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderOperationDO;
import com.erp.module.production.dal.dataobject.MfgReportDO;
import com.erp.module.production.dal.mapper.MfgDefectMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderOperationMapper;
import com.erp.module.production.dal.mapper.MfgReportMapper;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.order.OrderProgressService;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 不良记录与良率（需求 09-06） */
@Service("mfgDefectService")
public class DefectService {

    private final MfgDefectMapper mapper;
    private final MfgReportMapper reportMapper;
    private final MfgProdOrderMapper orderMapper;
    private final MfgProdOrderOperationMapper operationMapper;
    private final ReportService reportService;
    private final OrderProgressService progress;
    private final MfgSupport support;
    private final ObjectProvider<DefectNcrCreator> ncrCreator;

    public DefectService(MfgDefectMapper mapper, MfgReportMapper reportMapper, MfgProdOrderMapper orderMapper, MfgProdOrderOperationMapper operationMapper,
                         ReportService reportService, OrderProgressService progress, MfgSupport support, ObjectProvider<DefectNcrCreator> ncrCreator) {
        this.mapper = mapper;
        this.reportMapper = reportMapper;
        this.orderMapper = orderMapper;
        this.operationMapper = operationMapper;
        this.reportService = reportService;
        this.progress = progress;
        this.support = support;
        this.ncrCreator = ncrCreator;
    }

    public MfgDefectDO getOrThrow(Long id) {
        MfgDefectDO d = id == null ? null : mapper.selectById(id);
        if (d == null || "DRAFT".equals(d.getDisposition())) throw new BizException(ProductionErrorCodes.DEFECT_NOT_EXISTS);
        return d;
    }

    public boolean ncrAvailable() {
        return ncrCreator.getIfAvailable() != null;
    }

    // ==================== 查询 ====================

    public PageResult<DefectRow> page(DefectQuery q) {
        LambdaQueryWrapper<MfgDefectDO> w = new LambdaQueryWrapper<MfgDefectDO>().eq(MfgDefectDO::getDeleted, false).ne(MfgDefectDO::getDisposition, "DRAFT");
        if (q.getProdOrderId() != null) w.eq(MfgDefectDO::getProdOrderId, q.getProdOrderId());
        if (StringUtils.hasText(q.getProdOrderNo())) {
            List<Long> ids = orderMapper.selectList(new LambdaQueryWrapper<MfgProdOrderDO>().likeRight(MfgProdOrderDO::getDocNo, q.getProdOrderNo().trim()))
                    .stream().map(MfgProdOrderDO::getId).toList();
            w.in(MfgDefectDO::getProdOrderId, ids.isEmpty() ? List.of(0L) : ids);
        }
        if (q.getMaterialId() != null) w.eq(MfgDefectDO::getMaterialId, q.getMaterialId());
        if (q.getOperationSeq() != null) w.eq(MfgDefectDO::getOperationSeq, q.getOperationSeq());
        if (StringUtils.hasText(q.getDefectCode())) w.eq(MfgDefectDO::getDefectCode, q.getDefectCode());
        if (StringUtils.hasText(q.getDispositions())) w.in(MfgDefectDO::getDisposition, Arrays.asList(q.getDispositions().split(",")));
        if (q.getDateFrom() != null) w.ge(MfgDefectDO::getReportDate, q.getDateFrom());
        if (q.getDateTo() != null) w.le(MfgDefectDO::getReportDate, q.getDateTo());
        w.orderByDesc(MfgDefectDO::getReportDate).orderByDesc(MfgDefectDO::getId);
        IPage<MfgDefectDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    private List<DefectRow> rows(List<MfgDefectDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, MfgProdOrderDO> orders = orderMapper.selectBatchIds(list.stream().map(MfgDefectDO::getProdOrderId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MfgProdOrderDO::getId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(MfgDefectDO::getMaterialId).toList());
        Map<String, MfgProdOrderOperationDO> ops = operationMapper.selectByParents(orders.keySet()).stream()
                .collect(Collectors.toMap(op -> op.getProdOrderId() + "#" + op.getSeq(), Function.identity(), (a, b) -> a));
        Map<Long, String> reportNos = reportMapper.selectBatchIds(list.stream().map(MfgDefectDO::getReportId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(MfgReportDO::getId, MfgReportDO::getDocNo));
        Map<Long, UserDTO> users = support.users(list.stream().map(MfgDefectDO::getHandledBy).toList());
        return list.stream().map(d -> {
            MfgProdOrderDO o = orders.get(d.getProdOrderId());
            MaterialDTO m = ms.get(d.getMaterialId());
            MfgProdOrderOperationDO op = ops.get(d.getProdOrderId() + "#" + d.getOperationSeq());
            return new DefectRow(d.getId(), d.getReportId(), reportNos.get(d.getReportId()), d.getReportDate(), d.getProdOrderId(), o == null ? null : o.getDocNo(),
                    d.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), d.getOperationSeq(), op == null ? null : op.getOperation(),
                    d.getDefectCode(), d.getQty(), d.getPosition(), d.getDescription(), ReportService.fileIds(d.getImageFileIds()), d.getDisposition(),
                    d.getRepairedQty(), d.getScrappedQty(), pending(d), d.getNcrNo(), MfgSupport.name(users, d.getHandledBy()), d.getHandledAt());
        }).toList();
    }

    static BigDecimal pending(MfgDefectDO d) {
        return MfgSupport.max0(d.getQty().subtract(d.getRepairedQty()).subtract(d.getScrappedQty()));
    }

    // ==================== 处置 ====================

    /** 返修完成：返修合格数量作为本工序合格补报（DEF-R02、R03） */
    @Transactional(rollbackFor = Exception.class)
    public void repair(Long id, BigDecimal qty) {
        MfgDefectDO d = handleable(id, qty);
        reportService.supplementary(d, ReportService.REPAIR, qty, null);
    }

    /** 报废：本工序报废数增加 */
    @Transactional(rollbackFor = Exception.class)
    public void scrap(Long id, BigDecimal qty, String reason) {
        MfgDefectDO d = handleable(id, qty);
        if (!StringUtils.hasText(reason)) throw new BizException(ProductionErrorCodes.REPORT_SCRAP_REASON);
        reportService.supplementary(d, ReportService.SCRAP, qty, reason);
    }

    private MfgDefectDO handleable(Long id, BigDecimal qty) {
        MfgDefectDO d = getOrThrow(id);
        MfgProdOrderDO o = progress.getOrThrow(d.getProdOrderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        if (qty == null || qty.signum() <= 0) throw BizException.of(ProductionErrorCodes.LINE_QTY_POSITIVE, 1);
        if (qty.compareTo(pending(d)) > 0) throw new BizException(ProductionErrorCodes.DEFECT_OVER_HANDLE);
        return d;
    }

    /** 生成 NCR（品质模块实现 {@link DefectNcrCreator}） */
    @Transactional(rollbackFor = Exception.class)
    public String toNcr(Long id) {
        MfgDefectDO d = getOrThrow(id);
        if (d.getNcrNo() != null) throw BizException.of(ProductionErrorCodes.DEFECT_NCR_EXISTS, d.getNcrNo());
        DefectNcrCreator creator = ncrCreator.getIfAvailable();
        if (creator == null) throw new BizException(ProductionErrorCodes.DEFECT_NCR_UNAVAILABLE);
        MfgProdOrderDO o = progress.getOrThrow(d.getProdOrderId());
        String no = creator.create(new DefectNcrCreator.NcrRequest(d.getId(), o.getId(), o.getDocNo(), d.getMaterialId(), o.getBatchNo(), d.getOperationSeq(),
                d.getDefectCode(), d.getQty(), d.getDescription(), ReportService.fileIds(d.getImageFileIds())));
        d.setNcrNo(no);
        d.setHandledBy(support.currentUser());
        d.setHandledAt(LocalDateTime.now());
        mapper.updateByIdOrFail(d);
        return no;
    }

    // ==================== 良率 ====================

    /**
     * 良率报表（3.2）：按一次报工口径（正常报工），投入 = 合格 + 不良 + 报废；一次良率 = 合格 ÷ 投入；
     * 最终良率 = (合格 + 返修合格) ÷ 投入；FPY = Π 各报工点一次良率。
     */
    public YieldReport yield(YieldQuery q) {
        LocalDate from = q.getFrom() != null ? q.getFrom() : LocalDate.now().withDayOfMonth(1);
        LocalDate to = q.getTo() != null ? q.getTo() : LocalDate.now();
        LambdaQueryWrapper<MfgReportDO> w = new LambdaQueryWrapper<MfgReportDO>().eq(MfgReportDO::getDeleted, false).eq(MfgReportDO::getStatus, DocStatus.APPROVED)
                .ge(MfgReportDO::getReportDate, from).le(MfgReportDO::getReportDate, to);
        if (q.getDeptId() != null) w.in(MfgReportDO::getDeptId, support.deptAndChildren(q.getDeptId()));
        if (q.getOperationSeq() != null) w.eq(MfgReportDO::getOperationSeq, q.getOperationSeq());
        List<MfgReportDO> reports = reportMapper.selectScopedList(w);
        Map<Long, MfgProdOrderDO> orders = reports.isEmpty() ? Map.of()
                : orderMapper.selectBatchIds(reports.stream().map(MfgReportDO::getProdOrderId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(MfgProdOrderDO::getId, Function.identity()));
        if (q.getMaterialId() != null) {
            reports = reports.stream().filter(r -> orders.containsKey(r.getProdOrderId()) && q.getMaterialId().equals(orders.get(r.getProdOrderId()).getMaterialId()))
                    .toList();
        }
        Map<Long, MaterialDTO> ms = support.materials(orders.values().stream().map(MfgProdOrderDO::getMaterialId).toList());
        Map<Long, WorkCenterDTO> wcs = support.workCenters();
        Map<String, MfgProdOrderOperationDO> ops = operationMapper.selectByParents(orders.keySet()).stream()
                .collect(Collectors.toMap(op -> op.getProdOrderId() + "#" + op.getSeq(), Function.identity(), (a, b) -> a));
        String groupBy = StringUtils.hasText(q.getGroupBy()) ? q.getGroupBy() : "PRODUCT";
        Map<String, Acc> groups = new LinkedHashMap<>();
        Map<Integer, Acc> byOp = new TreeMap<>();
        Map<LocalDate, Acc> byDate = new TreeMap<>();
        Acc total = new Acc("ALL", "合计");
        for (MfgReportDO r : reports) {
            MfgProdOrderDO o = orders.get(r.getProdOrderId());
            MfgProdOrderOperationDO op = ops.get(r.getProdOrderId() + "#" + r.getOperationSeq());
            String[] key = switch (groupBy) {
                case "OPERATION" -> new String[]{String.valueOf(r.getOperationSeq()), r.getOperationSeq() + " " + (op == null ? "" : op.getOperation())};
                case "WORK_CENTER" -> new String[]{String.valueOf(r.getWorkCenterId()),
                        r.getWorkCenterId() == null || !wcs.containsKey(r.getWorkCenterId()) ? "-" : wcs.get(r.getWorkCenterId()).name()};
                case "SHIFT" -> new String[]{r.getShift(), support.dictLabel("mfg_shift", r.getShift())};
                case "DATE" -> new String[]{r.getReportDate().toString(), r.getReportDate().toString()};
                default -> {
                    MaterialDTO m = o == null ? null : ms.get(o.getMaterialId());
                    yield new String[]{String.valueOf(o == null ? null : o.getMaterialId()), m == null ? "-" : m.code() + " " + m.name()};
                }
            };
            Acc g = groups.computeIfAbsent(key[0], k -> new Acc(key[0], key[1]));
            g.add(r);
            total.add(r);
            byOp.computeIfAbsent(r.getOperationSeq(), k -> new Acc(String.valueOf(k), k + " " + (op == null ? "" : op.getOperation()))).add(r);
            byDate.computeIfAbsent(r.getReportDate(), k -> new Acc(k.toString(), k.toString())).add(r);
        }
        BigDecimal fpy = BigDecimal.ONE;
        boolean anyOp = false;
        for (Acc a : byOp.values()) {
            if (a.input.signum() == 0) continue;
            fpy = fpy.multiply(a.good.divide(a.input, 8, RoundingMode.HALF_UP));
            anyOp = true;
        }
        List<TrendPoint> trend = byDate.values().stream().map(a -> new TrendPoint(LocalDate.parse(a.key), a.firstYield(), a.input)).toList();
        return new YieldReport(total.input, total.firstYield(), anyOp ? fpy.setScale(4, RoundingMode.HALF_UP) : null,
                groups.values().stream().map(Acc::row).toList(), byOp.values().stream().map(Acc::row).toList(), trend, pareto(from, to, q, orders.keySet()));
    }

    /** 不良代码 Pareto：按数量降序，累计百分比 */
    private List<ParetoItem> pareto(LocalDate from, LocalDate to, YieldQuery q, Set<Long> orderIds) {
        if (orderIds.isEmpty()) return List.of();
        LambdaQueryWrapper<MfgDefectDO> w = new LambdaQueryWrapper<MfgDefectDO>().ne(MfgDefectDO::getDisposition, "DRAFT")
                .ge(MfgDefectDO::getReportDate, from).le(MfgDefectDO::getReportDate, to).in(MfgDefectDO::getProdOrderId, orderIds);
        if (q.getMaterialId() != null) w.eq(MfgDefectDO::getMaterialId, q.getMaterialId());
        if (q.getOperationSeq() != null) w.eq(MfgDefectDO::getOperationSeq, q.getOperationSeq());
        Map<String, BigDecimal> sum = new LinkedHashMap<>();
        for (MfgDefectDO d : mapper.selectList(w)) sum.merge(d.getDefectCode(), d.getQty(), BigDecimal::add);
        BigDecimal total = MfgSupport.sum(sum.values());
        List<ParetoItem> out = new ArrayList<>();
        BigDecimal cum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : sum.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).toList()) {
            cum = cum.add(e.getValue());
            out.add(new ParetoItem(e.getKey(), support.dictLabel("mfg_defect_code", e.getKey()), e.getValue(),
                    ratio(e.getValue(), total), ratio(cum, total)));
        }
        return out;
    }

    static BigDecimal ratio(BigDecimal a, BigDecimal b) {
        return b == null || b.signum() == 0 ? null : a.divide(b, 4, RoundingMode.HALF_UP);
    }

    /** 分组累计：正常报工计入投入与一次合格，返修补报计入返修合格 */
    static final class Acc {
        final String key;
        final String label;
        BigDecimal input = BigDecimal.ZERO;
        BigDecimal good = BigDecimal.ZERO;
        BigDecimal defect = BigDecimal.ZERO;
        BigDecimal scrap = BigDecimal.ZERO;
        BigDecimal repaired = BigDecimal.ZERO;

        Acc(String key, String label) {
            this.key = key;
            this.label = label;
        }

        void add(MfgReportDO r) {
            if (ReportService.NORMAL.equals(r.getReportKind())) {
                input = input.add(r.getGoodQty()).add(r.getDefectQty()).add(r.getScrapQty());
                good = good.add(r.getGoodQty());
                defect = defect.add(r.getDefectQty());
                scrap = scrap.add(r.getScrapQty());
            } else if (ReportService.REPAIR.equals(r.getReportKind())) {
                repaired = repaired.add(r.getGoodQty());
            }
        }

        BigDecimal firstYield() {
            return ratio(good, input);
        }

        YieldRow row() {
            return new YieldRow(key, label, input, good, defect, scrap, repaired, firstYield(), ratio(good.add(repaired), input), ratio(defect, input),
                    ratio(scrap, input));
        }
    }
}
