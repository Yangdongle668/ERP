package com.erp.module.quality.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.category.MaterialCategoryApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.controller.vo.ReportVOs.CapaOverdue;
import com.erp.module.quality.controller.vo.ReportVOs.CountRow;
import com.erp.module.quality.controller.vo.ReportVOs.IqcReport;
import com.erp.module.quality.controller.vo.ReportVOs.LotRow;
import com.erp.module.quality.controller.vo.ReportVOs.NcrCapaComplaintReport;
import com.erp.module.quality.controller.vo.ReportVOs.OutgoingReport;
import com.erp.module.quality.controller.vo.ReportVOs.ParetoRow;
import com.erp.module.quality.controller.vo.ReportVOs.ProcessReport;
import com.erp.module.quality.controller.vo.ReportVOs.ReportQuery;
import com.erp.module.quality.dal.dataobject.QcCapaDO;
import com.erp.module.quality.dal.dataobject.QcComplaintDO;
import com.erp.module.quality.dal.dataobject.QcDefectCodeDO;
import com.erp.module.quality.dal.dataobject.QcInspectionDO;
import com.erp.module.quality.dal.dataobject.QcInspectionDefectDO;
import com.erp.module.quality.dal.dataobject.QcNcrDO;
import com.erp.module.quality.dal.dataobject.QcNcrDispositionDO;
import com.erp.module.quality.dal.mapper.QcCapaMapper;
import com.erp.module.quality.dal.mapper.QcComplaintMapper;
import com.erp.module.quality.dal.mapper.QcDefectCodeMapper;
import com.erp.module.quality.dal.mapper.QcInspectionDefectMapper;
import com.erp.module.quality.dal.mapper.QcInspectionMapper;
import com.erp.module.quality.dal.mapper.QcNcrDispositionMapper;
import com.erp.module.quality.dal.mapper.QcNcrMapper;
import com.erp.module.quality.service.CapaStatus;
import com.erp.module.quality.service.ComplaintStatus;
import com.erp.module.quality.service.QcSupport;
import com.erp.module.quality.service.inspection.InspectionService;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 质量报表（10-07 第 2 节）：来料、制程、成品与出货、NCR / CAPA / 客诉、缺陷 Pareto */
@Service
public class QualityReportService {

    static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    static final Map<String, String> DISP_NAMES = Map.of("RETURN", "退货", "CONCESSION", "特采", "SORT", "挑选", "REWORK", "返工", "SCRAP", "报废");
    static final Map<String, String> SEVERITY_NAMES = Map.of("CRITICAL", "致命", "MAJOR", "严重", "MINOR", "轻微");

    private final QcInspectionMapper inspectionMapper;
    private final QcInspectionDefectMapper defectMapper;
    private final QcDefectCodeMapper defectCodeMapper;
    private final QcNcrMapper ncrMapper;
    private final QcNcrDispositionMapper dispMapper;
    private final QcCapaMapper capaMapper;
    private final QcComplaintMapper complaintMapper;
    private final MaterialCategoryApi categoryApi;
    private final QcSupport support;

    public QualityReportService(QcInspectionMapper inspectionMapper, QcInspectionDefectMapper defectMapper, QcDefectCodeMapper defectCodeMapper,
                                QcNcrMapper ncrMapper, QcNcrDispositionMapper dispMapper, QcCapaMapper capaMapper, QcComplaintMapper complaintMapper,
                                MaterialCategoryApi categoryApi, QcSupport support) {
        this.inspectionMapper = inspectionMapper;
        this.defectMapper = defectMapper;
        this.defectCodeMapper = defectCodeMapper;
        this.ncrMapper = ncrMapper;
        this.dispMapper = dispMapper;
        this.capaMapper = capaMapper;
        this.complaintMapper = complaintMapper;
        this.categoryApi = categoryApi;
        this.support = support;
    }

    static LocalDate from(ReportQuery q) {
        return q.from() != null ? q.from() : LocalDate.now().withDayOfMonth(1);
    }

    static LocalDate to(ReportQuery q) {
        return q.to() != null ? q.to() : LocalDate.now();
    }

    /** 期间内已判定的检验单（按判定时间） */
    private List<QcInspectionDO> judged(ReportQuery q, List<String> types) {
        List<QcInspectionDO> list = inspectionMapper.selectList(new LambdaQueryWrapper<QcInspectionDO>().in(QcInspectionDO::getInspectType, types)
                .isNotNull(QcInspectionDO::getResult)
                .ge(QcInspectionDO::getJudgeAt, from(q).atStartOfDay()).lt(QcInspectionDO::getJudgeAt, to(q).plusDays(1).atStartOfDay())
                .eq(q.supplierId() != null, QcInspectionDO::getSupplierId, q.supplierId())
                .eq(q.materialId() != null, QcInspectionDO::getMaterialId, q.materialId()));
        if (q.categoryId() == null || list.isEmpty()) return list;
        Set<Long> cats = new HashSet<>(categoryApi.getDescendantIds(q.categoryId()));
        cats.add(q.categoryId());
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(QcInspectionDO::getMaterialId).toList());
        return list.stream().filter(d -> ms.containsKey(d.getMaterialId()) && cats.contains(ms.get(d.getMaterialId()).categoryId())).toList();
    }

    static LotRow lot(String key, String name, List<QcInspectionDO> list) {
        int q = count(list, InspectionService.QUALIFIED);
        int c = count(list, InspectionService.CONCESSION);
        int r = count(list, InspectionService.REJECTED);
        int s = count(list, InspectionService.SORTED);
        BigDecimal qty = list.stream().map(QcInspectionDO::getLotQty).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        int sample = list.stream().mapToInt(d -> d.getSampleQty() == null ? 0 : d.getSampleQty()).sum();
        int defects = list.stream().mapToInt(d -> nz(d.getCrCount()) + nz(d.getMaCount()) + nz(d.getMiCount())).sum();
        return new LotRow(key, name, list.size(), q, c, r, s, pct(q, list.size()), qty, sample, defects, pct(defects, sample));
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static int count(List<QcInspectionDO> list, String result) {
        return (int) list.stream().filter(d -> result.equals(d.getResult())).count();
    }

    static BigDecimal pct(long a, long b) {
        return b == 0 ? null : BigDecimal.valueOf(a * 100L).divide(BigDecimal.valueOf(b), 2, RoundingMode.HALF_UP);
    }

    private List<LotRow> byMonth(List<QcInspectionDO> list) {
        Map<String, List<QcInspectionDO>> m = new TreeMap<>(list.stream().collect(Collectors.groupingBy(d -> d.getJudgeAt().format(MONTH))));
        return m.entrySet().stream().map(e -> lot(e.getKey(), e.getKey(), e.getValue())).toList();
    }

    private List<LotRow> byMaterial(List<QcInspectionDO> list) {
        Map<Long, List<QcInspectionDO>> m = list.stream().collect(Collectors.groupingBy(QcInspectionDO::getMaterialId));
        Map<Long, MaterialDTO> ms = support.materials(m.keySet());
        return m.entrySet().stream().map(e -> {
            MaterialDTO md = ms.get(e.getKey());
            return lot(md == null ? String.valueOf(e.getKey()) : md.code(), md == null ? "" : md.name(), e.getValue());
        }).sorted(Comparator.comparing(LotRow::lots).reversed()).toList();
    }

    // ==================== 来料 ====================

    /** 来料质量：供应商按批次合格率升序（最低在前） */
    public IqcReport iqc(ReportQuery q) {
        List<QcInspectionDO> list = judged(q, List.of(InspectType.IQC.name(), InspectType.RECHECK.name()));
        Map<Long, List<QcInspectionDO>> bySup = list.stream().filter(d -> d.getSupplierId() != null).collect(Collectors.groupingBy(QcInspectionDO::getSupplierId));
        Map<Long, SupplierDTO> sups = support.suppliers(bySup.keySet());
        List<LotRow> supplierRows = bySup.entrySet().stream().map(e -> {
            SupplierDTO s = sups.get(e.getKey());
            return lot(s == null ? String.valueOf(e.getKey()) : s.code(), s == null ? "" : s.name(), e.getValue());
        }).sorted(Comparator.comparing((LotRow r) -> r.passRate() == null ? BigDecimal.valueOf(101) : r.passRate()).thenComparing(LotRow::key)).toList();
        return new IqcReport(lot("TOTAL", "合计", list), supplierRows, byMaterial(list), byMonth(list));
    }

    // ==================== 制程 ====================

    public ProcessReport process(ReportQuery q) {
        List<QcInspectionDO> list = judged(q, List.of(InspectType.IPQC.name()));
        return new ProcessReport(lot("TOTAL", "合计", list), byMonth(list), byMaterial(list), inspectionPareto(list));
    }

    // ==================== 成品与出货 ====================

    public OutgoingReport outgoing(ReportQuery q) {
        List<QcInspectionDO> fqc = judged(q, List.of(InspectType.FQC.name()));
        List<QcInspectionDO> oqc = judged(q, List.of(InspectType.OQC.name()));
        long firstPass = fqc.stream().filter(d -> InspectionService.QUALIFIED.equals(d.getResult()) && nz(d.getRejudgeCount()) == 0).count();
        long complaints = complaintMapper.selectCount(new LambdaQueryWrapper<QcComplaintDO>().ne(QcComplaintDO::getComplaintStatus, ComplaintStatus.CANCELED.name())
                .ge(QcComplaintDO::getReceivedAt, from(q).atStartOfDay()).lt(QcComplaintDO::getReceivedAt, to(q).plusDays(1).atStartOfDay())
                .eq(q.materialId() != null, QcComplaintDO::getMaterialId, q.materialId()));
        return new OutgoingReport(lot("FQC", "FQC", fqc), pct(firstPass, fqc.size()), lot("OQC", "OQC", oqc), oqc.size(), (int) complaints,
                pct(complaints, oqc.size()), byMonth(fqc), byMonth(oqc));
    }

    // ==================== NCR / CAPA / 客诉 ====================

    public NcrCapaComplaintReport ncrCapaComplaint(ReportQuery q) {
        LocalDate from = from(q);
        LocalDate to = to(q);
        List<QcNcrDO> ncrs = ncrMapper.selectList(new LambdaQueryWrapper<QcNcrDO>().ne(QcNcrDO::getStatus, DocStatus.VOIDED)
                .ge(QcNcrDO::getDocDate, from).le(QcNcrDO::getDocDate, to).eq(q.materialId() != null, QcNcrDO::getMaterialId, q.materialId())
                .eq(q.supplierId() != null, QcNcrDO::getSupplierId, q.supplierId()));
        List<CountRow> bySource = group(ncrs, QcNcrDO::getNcrSource, k -> k, QcNcrDO::getNcrQty);
        List<CountRow> byResp = group(ncrs, QcNcrDO::getResponsibility, k -> support.dictLabel("qc_ncr_responsibility", k), QcNcrDO::getNcrQty);
        Map<Long, MaterialDTO> ms = support.materials(ncrs.stream().map(QcNcrDO::getMaterialId).toList());
        List<CountRow> byMat = group(ncrs, n -> ms.containsKey(n.getMaterialId()) ? ms.get(n.getMaterialId()).code() : String.valueOf(n.getMaterialId()),
                k -> ms.values().stream().filter(m -> m.code().equals(k)).map(MaterialDTO::name).findFirst().orElse(""), QcNcrDO::getNcrQty);
        byMat = byMat.stream().limit(20).toList();
        List<QcNcrDispositionDO> disps = dispMapper.selectByParents(ncrs.stream().map(QcNcrDO::getId).toList());
        Map<String, List<QcNcrDispositionDO>> dg = disps.stream().collect(Collectors.groupingBy(QcNcrDispositionDO::getDisposition, LinkedHashMap::new, Collectors.toList()));
        List<CountRow> byDisp = dg.entrySet().stream().map(e -> new CountRow(e.getKey(), DISP_NAMES.getOrDefault(e.getKey(), e.getKey()), e.getValue().size(),
                e.getValue().stream().map(QcNcrDispositionDO::getQty).reduce(BigDecimal.ZERO, BigDecimal::add))).toList();
        List<QcNcrDO> closed = ncrs.stream().filter(n -> n.getClosedAt() != null && n.getCreatedAt() != null).toList();
        BigDecimal avgClose = closed.isEmpty() ? null : BigDecimal.valueOf(closed.stream().mapToLong(n -> ChronoUnit.DAYS.between(n.getCreatedAt().toLocalDate(),
                n.getClosedAt().toLocalDate())).sum()).divide(BigDecimal.valueOf(closed.size()), 1, RoundingMode.HALF_UP);

        List<QcCapaDO> capas = capaMapper.selectList(new LambdaQueryWrapper<QcCapaDO>().ne(QcCapaDO::getCapaStatus, CapaStatus.CANCELED.name())
                .ge(QcCapaDO::getDocDate, from).le(QcCapaDO::getDocDate, to));
        List<QcCapaDO> capaClosed = capas.stream().filter(c -> CapaStatus.CLOSED.name().equals(c.getCapaStatus()) && c.getClosedAt() != null).toList();
        long onTime = capaClosed.stream().filter(c -> !c.getClosedAt().toLocalDate().isAfter(c.getDueDate())).count();
        BigDecimal capaAvg = capaClosed.isEmpty() ? null : BigDecimal.valueOf(capaClosed.stream().mapToLong(c -> ChronoUnit.DAYS.between(c.getCreatedAt().toLocalDate(),
                c.getClosedAt().toLocalDate())).sum()).divide(BigDecimal.valueOf(capaClosed.size()), 1, RoundingMode.HALF_UP);
        LocalDate today = LocalDate.now();
        List<QcCapaDO> overdue = capaMapper.selectList(new LambdaQueryWrapper<QcCapaDO>().in(QcCapaDO::getCapaStatus, List.of(CapaStatus.OPEN.name(), CapaStatus.VERIFYING.name()))
                .lt(QcCapaDO::getDueDate, today).orderByAsc(QcCapaDO::getDueDate));
        Map<Long, UserDTO> users = support.users(overdue.stream().map(QcCapaDO::getLeaderId).toList());
        List<CapaOverdue> overdueRows = overdue.stream().map(c -> new CapaOverdue(c.getId(), c.getDocNo(), c.getTitle(), QcSupport.name(users, c.getLeaderId()),
                c.getDueDate(), (int) ChronoUnit.DAYS.between(c.getDueDate(), today), c.getCurrentStep())).toList();

        List<QcComplaintDO> cpls = complaintMapper.selectScopedList(new LambdaQueryWrapper<QcComplaintDO>().eq(QcComplaintDO::getDeleted, false)
                .ne(QcComplaintDO::getComplaintStatus, ComplaintStatus.CANCELED.name())
                .ge(QcComplaintDO::getReceivedAt, from.atStartOfDay()).lt(QcComplaintDO::getReceivedAt, to.plusDays(1).atStartOfDay())
                .eq(q.materialId() != null, QcComplaintDO::getMaterialId, q.materialId()));
        long replyOnTime = cpls.stream().filter(c -> c.getRepliedAt() != null && !c.getRepliedAt().toLocalDate().isAfter(c.getReplyDueDate())).count();
        Map<Long, CustomerDTO> cus = support.customers(cpls.stream().map(QcComplaintDO::getCustomerId).toList());
        List<CountRow> byCustomer = group(cpls, c -> String.valueOf(c.getCustomerId()), k -> QcSupport.customerName(cus.get(Long.valueOf(k))), QcComplaintDO::getComplaintQty);
        List<CountRow> byType = group(cpls, QcComplaintDO::getComplaintType, k -> support.dictLabel("qc_complaint_type", k), QcComplaintDO::getComplaintQty);
        List<CountRow> bySeverity = group(cpls, QcComplaintDO::getSeverity, k -> SEVERITY_NAMES.getOrDefault(k, k), QcComplaintDO::getComplaintQty);
        List<CountRow> byMonth = group(cpls, c -> c.getReceivedAt().format(MONTH), k -> k, QcComplaintDO::getComplaintQty).stream()
                .sorted(Comparator.comparing(CountRow::key)).toList();
        return new NcrCapaComplaintReport(ncrs.size(), avgClose, bySource, byResp, byDisp, byMat, capas.size(), capaClosed.size(), pct(onTime, capaClosed.size()),
                capaAvg, overdueRows, cpls.size(), pct(replyOnTime, cpls.size()), byCustomer, byType, bySeverity, byMonth);
    }

    private static <T> List<CountRow> group(List<T> list, Function<T, String> key, Function<String, String> name, Function<T, BigDecimal> qty) {
        Map<String, List<T>> m = list.stream().filter(t -> key.apply(t) != null).collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
        return m.entrySet().stream().map(e -> new CountRow(e.getKey(), name.apply(e.getKey()), e.getValue().size(),
                        e.getValue().stream().map(qty).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(CountRow::count).reversed()).toList();
    }

    // ==================== 缺陷 Pareto ====================

    /** 来源：IQC / IPQC / FQC / OQC / RETURN 取检验缺陷明细；PRODUCTION / COMPLAINT 取 NCR 缺陷代码（按不合格数量） */
    public List<ParetoRow> pareto(ReportQuery q) {
        String source = StringUtils.hasText(q.source()) ? q.source() : "IQC";
        if ("PRODUCTION".equals(source) || "COMPLAINT".equals(source)) {
            List<QcNcrDO> ncrs = ncrMapper.selectList(new LambdaQueryWrapper<QcNcrDO>().eq(QcNcrDO::getNcrSource, source).ne(QcNcrDO::getStatus, DocStatus.VOIDED)
                    .ge(QcNcrDO::getDocDate, from(q)).le(QcNcrDO::getDocDate, to(q)).isNotNull(QcNcrDO::getDefectCodes));
            Map<String, Integer> qty = new LinkedHashMap<>();
            for (QcNcrDO n : ncrs) {
                int v = n.getNcrQty() == null ? 1 : n.getNcrQty().setScale(0, RoundingMode.HALF_UP).intValue();
                for (String c : n.getDefectCodes().split(",")) if (StringUtils.hasText(c)) qty.merge(c.trim(), v, Integer::sum);
            }
            return paretoRows(qty);
        }
        List<String> types = "IQC".equals(source) ? List.of("IQC", "RECHECK") : List.of(InspectType.valueOf(source).name());
        return inspectionPareto(judged(q, types));
    }

    private List<ParetoRow> inspectionPareto(List<QcInspectionDO> list) {
        List<QcInspectionDefectDO> defects = defectMapper.selectByParents(list.stream().map(QcInspectionDO::getId).toList());
        Map<String, Integer> qty = new LinkedHashMap<>();
        for (QcInspectionDefectDO d : defects) qty.merge(d.getDefectCode(), d.getQty(), Integer::sum);
        return paretoRows(qty);
    }

    private List<ParetoRow> paretoRows(Map<String, Integer> qty) {
        int total = qty.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, String> names = qty.isEmpty() ? Map.of() : defectCodeMapper.selectList(new LambdaQueryWrapper<QcDefectCodeDO>().in(QcDefectCodeDO::getCode, qty.keySet()))
                .stream().collect(Collectors.toMap(QcDefectCodeDO::getCode, QcDefectCodeDO::getName, (a, b) -> a));
        List<ParetoRow> rows = new ArrayList<>();
        int cum = 0;
        for (Map.Entry<String, Integer> e : qty.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).toList()) {
            cum += e.getValue();
            rows.add(new ParetoRow(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()), e.getValue(), pct(e.getValue(), total), pct(cum, total)));
        }
        return rows;
    }
}
