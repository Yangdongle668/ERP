package com.erp.module.production.service.center;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.result.PageResult;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.production.controller.vo.ReportCenterVOs.Achievement;
import com.erp.module.production.controller.vo.ReportCenterVOs.AchievementQuery;
import com.erp.module.production.controller.vo.ReportCenterVOs.DelayedOrder;
import com.erp.module.production.controller.vo.ReportCenterVOs.OpProgress;
import com.erp.module.production.controller.vo.ReportCenterVOs.OutputQuery;
import com.erp.module.production.controller.vo.ReportCenterVOs.OutputRow;
import com.erp.module.production.controller.vo.ReportCenterVOs.ProgressQuery;
import com.erp.module.production.controller.vo.ReportCenterVOs.ProgressRow;
import com.erp.module.production.controller.vo.ReportCenterVOs.VarianceQuery;
import com.erp.module.production.controller.vo.ReportCenterVOs.VarianceRow;
import com.erp.module.production.dal.dataobject.MfgIssueDO;
import com.erp.module.production.dal.dataobject.MfgIssueLineDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderMaterialDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderOperationDO;
import com.erp.module.production.dal.dataobject.MfgReportDO;
import com.erp.module.production.dal.dataobject.MfgReportOperatorDO;
import com.erp.module.production.dal.mapper.MfgIssueLineMapper;
import com.erp.module.production.dal.mapper.MfgIssueMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMaterialMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderOperationMapper;
import com.erp.module.production.dal.mapper.MfgReportMapper;
import com.erp.module.production.dal.mapper.MfgReportOperatorMapper;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.ProdStatus;
import com.erp.module.production.service.report.ReportService;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 生产报表（需求 09-08）：订单进度、领料差异、产量与工时、计划达成率。按车间数据权限过滤 */
@Service("mfgReportCenterService")
public class ReportCenterService {

    static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);
    static final List<String> OPEN = List.of(ProdStatus.RELEASED.name(), ProdStatus.IN_PROGRESS.name(), ProdStatus.SUSPENDED.name());

    private final MfgProdOrderMapper orderMapper;
    private final MfgProdOrderMaterialMapper materialMapper;
    private final MfgProdOrderOperationMapper operationMapper;
    private final MfgReportMapper reportMapper;
    private final MfgReportOperatorMapper operatorMapper;
    private final MfgIssueMapper issueMapper;
    private final MfgIssueLineMapper issueLineMapper;
    private final MfgSupport support;
    private final ObjectProvider<SalesOrderQueryApi> salesOrderQueryApi;

    public ReportCenterService(MfgProdOrderMapper orderMapper, MfgProdOrderMaterialMapper materialMapper, MfgProdOrderOperationMapper operationMapper,
                               MfgReportMapper reportMapper, MfgReportOperatorMapper operatorMapper, MfgIssueMapper issueMapper,
                               MfgIssueLineMapper issueLineMapper, MfgSupport support, ObjectProvider<SalesOrderQueryApi> salesOrderQueryApi) {
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.operationMapper = operationMapper;
        this.reportMapper = reportMapper;
        this.operatorMapper = operatorMapper;
        this.issueMapper = issueMapper;
        this.issueLineMapper = issueLineMapper;
        this.support = support;
        this.salesOrderQueryApi = salesOrderQueryApi;
    }

    // ==================== 2.1 订单进度 ====================

    public PageResult<ProgressRow> progress(ProgressQuery q) {
        IPage<MfgProdOrderDO> p = orderMapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), progressWrapper(q));
        return new PageResult<>(progressRows(p.getRecords()), p.getTotal());
    }

    public List<ProgressRow> progressAll(ProgressQuery q, int limit) {
        return progressRows(orderMapper.selectScopedList(progressWrapper(q).last("LIMIT " + limit)));
    }

    private LambdaQueryWrapper<MfgProdOrderDO> progressWrapper(ProgressQuery q) {
        LambdaQueryWrapper<MfgProdOrderDO> w = new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getDeleted, false)
                .in(MfgProdOrderDO::getProdStatus, OPEN);
        if (q.getDeptId() != null) w.in(MfgProdOrderDO::getDeptId, support.deptAndChildren(q.getDeptId()));
        if (q.getMaterialId() != null) w.eq(MfgProdOrderDO::getMaterialId, q.getMaterialId());
        if (q.getPlanEndFrom() != null) w.ge(MfgProdOrderDO::getPlanEnd, q.getPlanEndFrom());
        if (q.getPlanEndTo() != null) w.le(MfgProdOrderDO::getPlanEnd, q.getPlanEndTo());
        return w.orderByAsc(MfgProdOrderDO::getPlanEnd).orderByAsc(MfgProdOrderDO::getId);
    }

    private List<ProgressRow> progressRows(List<MfgProdOrderDO> orders) {
        if (orders.isEmpty()) return List.of();
        Map<Long, MaterialDTO> ms = support.materials(orders.stream().map(MfgProdOrderDO::getMaterialId).toList());
        Map<Long, List<MfgProdOrderOperationDO>> ops = operationMapper.selectByParents(orders.stream().map(MfgProdOrderDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(MfgProdOrderOperationDO::getProdOrderId));
        Map<Long, OrgDTO> orgs = support.orgs(orders.stream().map(MfgProdOrderDO::getDeptId).toList());
        Map<Long, SalesOrderLineDTO> lines = new HashMap<>();
        SalesOrderQueryApi api = salesOrderQueryApi.getIfAvailable();
        List<Long> lineIds = orders.stream().map(MfgProdOrderDO::getSalesOrderLineId).filter(Objects::nonNull).distinct().toList();
        if (api != null && !lineIds.isEmpty()) lines.putAll(api.getLines(lineIds));
        LocalDate today = LocalDate.now();
        return orders.stream().map(o -> {
            MaterialDTO m = ms.get(o.getMaterialId());
            List<OpProgress> opList = ops.getOrDefault(o.getId(), List.of()).stream().filter(op -> Boolean.TRUE.equals(op.getIsReportPoint()))
                    .map(op -> new OpProgress(op.getSeq(), op.getOperation(), op.getGoodQty(), op.getGoodQty().compareTo(o.getQty()) >= 0)).toList();
            SalesOrderLineDTO line = lines.get(o.getSalesOrderLineId());
            return new ProgressRow(o.getId(), o.getDocNo(), o.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), o.getQty(), opList,
                    o.getCompletedQty(), o.getStockedQty(), o.getPlanEnd(), o.getPlanEnd().isBefore(today), o.getProdStatus(), o.getSalesOrderNo(),
                    line == null ? null : line.dueDate(), MfgSupport.orgName(orgs, o.getDeptId()));
        }).toList();
    }

    // ==================== 2.2 领料差异 ====================

    public List<VarianceRow> variance(VarianceQuery q) {
        LambdaQueryWrapper<MfgProdOrderDO> w = new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getDeleted, false);
        if (q.getProdOrderId() != null) {
            w.eq(MfgProdOrderDO::getId, q.getProdOrderId());
        } else {
            w.eq(MfgProdOrderDO::getProdStatus, ProdStatus.CLOSED.name());
            if (q.getClosedFrom() != null) w.ge(MfgProdOrderDO::getActualEnd, q.getClosedFrom().atStartOfDay());
            if (q.getClosedTo() != null) w.lt(MfgProdOrderDO::getActualEnd, q.getClosedTo().plusDays(1).atStartOfDay());
        }
        if (q.getDeptId() != null) w.in(MfgProdOrderDO::getDeptId, support.deptAndChildren(q.getDeptId()));
        List<MfgProdOrderDO> orders = orderMapper.selectScopedList(w.last("LIMIT 2000"));
        if (orders.isEmpty()) return List.of();
        Map<Long, MfgProdOrderDO> byId = orders.stream().collect(Collectors.toMap(MfgProdOrderDO::getId, Function.identity()));
        List<MfgProdOrderMaterialDO> mats = materialMapper.selectByParents(byId.keySet()).stream()
                .filter(m -> q.getMaterialId() == null || q.getMaterialId().equals(m.getComponentId())).toList();
        Set<Long> matIds = new HashSet<>(mats.stream().map(MfgProdOrderMaterialDO::getComponentId).toList());
        matIds.addAll(orders.stream().map(MfgProdOrderDO::getMaterialId).toList());
        Map<Long, MaterialDTO> ms = support.materials(matIds);
        Map<Long, Set<String>> reasons = overReasons(byId.keySet());
        List<VarianceRow> out = new ArrayList<>();
        for (MfgProdOrderMaterialDO m : mats) {
            MfgProdOrderDO o = byId.get(m.getProdOrderId());
            MaterialDTO c = ms.get(m.getComponentId());
            MaterialDTO p = ms.get(o.getMaterialId());
            BigDecimal done = o.getCompletedQty().add(o.getScrappedQty());
            BigDecimal theo = support.roundUp(done.multiply(m.getQtyPer()).multiply(BigDecimal.ONE.add(MfgSupport.nz(m.getScrapRate()))),
                    c == null ? null : c.baseUom());
            BigDecimal net = MfgSupport.nz(m.getIssuedQty()).subtract(MfgSupport.nz(m.getReturnedQty()));
            BigDecimal diff = net.subtract(theo);
            BigDecimal rate = theo.signum() == 0 ? null : diff.divide(theo, 4, RoundingMode.HALF_UP);
            String rs = reasons.getOrDefault(m.getId(), Set.of()).stream().map(r -> support.dictLabel("mfg_over_issue_reason", r))
                    .collect(Collectors.joining("、"));
            out.add(new VarianceRow(o.getId(), o.getDocNo(), p == null ? null : p.code(), m.getComponentId(), c == null ? null : c.code(),
                    c == null ? null : c.name(), c == null ? null : c.baseUom(), theo, net, diff, rate, m.getOverIssuedQty(), rs.isEmpty() ? null : rs));
        }
        out.sort((a, b) -> {
            BigDecimal x = a.varianceRate() == null ? BigDecimal.ZERO : a.varianceRate().abs();
            BigDecimal y = b.varianceRate() == null ? BigDecimal.ZERO : b.varianceRate().abs();
            return y.compareTo(x);
        });
        return out;
    }

    /** 已完成超领单的原因，键：用料行 */
    private Map<Long, Set<String>> overReasons(Set<Long> orderIds) {
        List<MfgIssueDO> issues = issueMapper.selectList(new LambdaQueryWrapper<MfgIssueDO>().in(MfgIssueDO::getProdOrderId, orderIds)
                .eq(MfgIssueDO::getIssueType, "OVER").eq(MfgIssueDO::getStatus, DocStatus.COMPLETED));
        Map<Long, Set<String>> map = new HashMap<>();
        if (issues.isEmpty()) return map;
        Map<Long, String> reason = issues.stream().collect(Collectors.toMap(MfgIssueDO::getId, i -> i.getOverReason() == null ? "" : i.getOverReason()));
        for (MfgIssueLineDO l : issueLineMapper.selectByParents(reason.keySet())) {
            String r = reason.get(l.getIssueId());
            if (!r.isEmpty()) map.computeIfAbsent(l.getMaterialLineId(), k -> new LinkedHashSet<>()).add(r);
        }
        return map;
    }

    // ==================== 2.3 产量与工时 ====================

    public List<OutputRow> output(OutputQuery q) {
        LocalDate from = q.getFrom() != null ? q.getFrom() : LocalDate.now().withDayOfMonth(1);
        LocalDate to = q.getTo() != null ? q.getTo() : LocalDate.now();
        LambdaQueryWrapper<MfgReportDO> w = new LambdaQueryWrapper<MfgReportDO>().eq(MfgReportDO::getDeleted, false).eq(MfgReportDO::getStatus, DocStatus.APPROVED)
                .ge(MfgReportDO::getReportDate, from).le(MfgReportDO::getReportDate, to).ne(MfgReportDO::getReportKind, ReportService.SCRAP);
        if (q.getDeptId() != null) w.in(MfgReportDO::getDeptId, support.deptAndChildren(q.getDeptId()));
        List<MfgReportDO> reports = reportMapper.selectScopedList(w);
        if (reports.isEmpty()) return List.of();
        Map<Long, MfgProdOrderDO> orders = orderMapper.selectBatchIds(reports.stream().map(MfgReportDO::getProdOrderId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(MfgProdOrderDO::getId, Function.identity()));
        Map<String, MfgProdOrderOperationDO> ops = operationMapper.selectByParents(orders.keySet()).stream()
                .collect(Collectors.toMap(op -> op.getProdOrderId() + "#" + op.getSeq(), Function.identity(), (a, b) -> a));
        Map<Long, List<MfgReportOperatorDO>> operators = operatorMapper.selectByParents(reports.stream().map(MfgReportDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(MfgReportOperatorDO::getReportId));
        Map<Long, MaterialDTO> ms = support.materials(orders.values().stream().map(MfgProdOrderDO::getMaterialId).toList());
        Map<Long, WorkCenterDTO> wcs = support.workCenters();
        Map<Long, OrgDTO> orgs = support.orgs(reports.stream().map(MfgReportDO::getDeptId).toList());
        Map<Long, UserDTO> users = support.users(operators.values().stream().flatMap(List::stream).map(MfgReportOperatorDO::getUserId).toList());
        String groupBy = q.getGroupBy() == null ? "DATE" : q.getGroupBy();
        Map<String, Acc> groups = new LinkedHashMap<>();
        for (MfgReportDO r : reports.stream().sorted((a, b) -> a.getReportDate().compareTo(b.getReportDate())).toList()) {
            MfgProdOrderOperationDO op = ops.get(r.getProdOrderId() + "#" + r.getOperationSeq());
            BigDecimal std = op == null ? BigDecimal.ZERO : r.getGoodQty().multiply(op.getStdRunSeconds()).divide(SECONDS_PER_HOUR, 6, RoundingMode.HALF_UP);
            List<MfgReportOperatorDO> people = operators.getOrDefault(r.getId(), List.of());
            if ("OPERATOR".equals(groupBy)) {
                if (people.isEmpty()) {
                    groups.computeIfAbsent("-", k -> new Acc("-", "（未登记人员）")).add(r.getGoodQty(), r.getScrapQty(), r.getWorkHours(), std, null);
                    continue;
                }
                for (MfgReportOperatorDO x : people) {
                    BigDecimal share = r.getWorkHours().signum() == 0 || x.getHours() == null
                            ? BigDecimal.ONE.divide(BigDecimal.valueOf(people.size()), 6, RoundingMode.HALF_UP)
                            : x.getHours().divide(r.getWorkHours(), 6, RoundingMode.HALF_UP);
                    String key = x.getUserId() != null ? "U" + x.getUserId() : "N" + x.getOperatorName();
                    String label = x.getUserId() != null ? MfgSupport.name(users, x.getUserId()) : x.getOperatorName();
                    groups.computeIfAbsent(key, k -> new Acc(key, label)).add(r.getGoodQty().multiply(share), r.getScrapQty().multiply(share),
                            x.getHours() != null ? x.getHours() : r.getWorkHours().multiply(share), std.multiply(share), key);
                }
                continue;
            }
            String[] key = switch (groupBy) {
                case "DEPT" -> new String[]{String.valueOf(r.getDeptId()), MfgSupport.orgName(orgs, r.getDeptId())};
                case "WORK_CENTER" -> new String[]{String.valueOf(r.getWorkCenterId()),
                        r.getWorkCenterId() == null || !wcs.containsKey(r.getWorkCenterId()) ? "-" : wcs.get(r.getWorkCenterId()).name()};
                case "SHIFT" -> new String[]{r.getShift(), support.dictLabel("mfg_shift", r.getShift())};
                case "PRODUCT" -> {
                    MfgProdOrderDO o = orders.get(r.getProdOrderId());
                    MaterialDTO m = o == null ? null : ms.get(o.getMaterialId());
                    yield new String[]{String.valueOf(o == null ? null : o.getMaterialId()), m == null ? "-" : m.code() + " " + m.name()};
                }
                default -> new String[]{r.getReportDate().toString(), r.getReportDate().toString()};
            };
            Acc g = groups.computeIfAbsent(key[0], k -> new Acc(key[0], key[1]));
            g.add(r.getGoodQty(), r.getScrapQty(), r.getWorkHours(), std, null);
            for (MfgReportOperatorDO x : people) g.people.add(x.getUserId() != null ? "U" + x.getUserId() : "N" + x.getOperatorName());
        }
        return groups.values().stream().map(Acc::row).toList();
    }

    static final class Acc {
        final String key;
        final String label;
        BigDecimal good = BigDecimal.ZERO;
        BigDecimal scrap = BigDecimal.ZERO;
        BigDecimal hours = BigDecimal.ZERO;
        BigDecimal std = BigDecimal.ZERO;
        final Set<String> people = new HashSet<>();

        Acc(String key, String label) {
            this.key = key;
            this.label = label;
        }

        void add(BigDecimal g, BigDecimal s, BigDecimal h, BigDecimal st, String person) {
            good = good.add(g);
            scrap = scrap.add(s);
            hours = hours.add(h);
            std = std.add(st);
            if (person != null) people.add(person);
        }

        OutputRow row() {
            int n = people.size();
            return new OutputRow(key, label, good.setScale(4, RoundingMode.HALF_UP), scrap.setScale(4, RoundingMode.HALF_UP), hours.setScale(2, RoundingMode.HALF_UP),
                    std.setScale(2, RoundingMode.HALF_UP), hours.signum() == 0 ? null : std.divide(hours, 4, RoundingMode.HALF_UP), n,
                    n == 0 ? null : good.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP));
        }
    }

    // ==================== 2.4 计划达成率 ====================

    public Achievement achievement(AchievementQuery q) {
        LocalDate from = q.getFrom() != null ? q.getFrom() : LocalDate.now().withDayOfMonth(1);
        LocalDate to = q.getTo() != null ? q.getTo() : LocalDate.now();
        LambdaQueryWrapper<MfgProdOrderDO> w = new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getDeleted, false)
                .ge(MfgProdOrderDO::getPlanEnd, from).le(MfgProdOrderDO::getPlanEnd, to).isNotNull(MfgProdOrderDO::getReleasedAt)
                .notIn(MfgProdOrderDO::getProdStatus, List.of(ProdStatus.VOIDED.name(), ProdStatus.PLANNED.name()));
        if (q.getDeptId() != null) w.in(MfgProdOrderDO::getDeptId, support.deptAndChildren(q.getDeptId()));
        List<MfgProdOrderDO> orders = orderMapper.selectScopedList(w);
        Map<Long, MaterialDTO> ms = support.materials(orders.stream().map(MfgProdOrderDO::getMaterialId).toList());
        LocalDate today = LocalDate.now();
        int onTime = 0;
        List<DelayedOrder> delayed = new ArrayList<>();
        for (MfgProdOrderDO o : orders) {
            boolean finished = ProdStatus.COMPLETED.name().equals(o.getProdStatus()) || (ProdStatus.CLOSED.name().equals(o.getProdStatus())
                    && o.getQualifiedStockedQty().add(o.getScrappedQty()).compareTo(o.getQty()) >= 0);
            LocalDate end = o.getActualEnd() == null ? null : o.getActualEnd().toLocalDate();
            if (finished && end != null && !end.isAfter(o.getPlanEnd())) {
                onTime++;
                continue;
            }
            LocalDate ref = finished && end != null ? end : today;
            int days = (int) Math.max(0, ChronoUnit.DAYS.between(o.getPlanEnd(), ref));
            if (!finished && !o.getPlanEnd().isBefore(today)) continue;
            MaterialDTO m = ms.get(o.getMaterialId());
            delayed.add(new DelayedOrder(o.getId(), o.getDocNo(), m == null ? null : m.code(), m == null ? null : m.name(), o.getPlanEnd(), end, days,
                    o.getProdStatus()));
        }
        int due = orders.size();
        delayed.sort((a, b) -> Integer.compare(b.delayDays(), a.delayDays()));
        return new Achievement(due, onTime, due == 0 ? null : BigDecimal.valueOf(onTime).divide(BigDecimal.valueOf(due), 4, RoundingMode.HALF_UP), delayed);
    }
}
