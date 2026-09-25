package com.erp.module.purchase.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.module.engineering.api.category.MaterialCategoryApi;
import com.erp.module.engineering.api.category.MaterialCategoryDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.controller.vo.ReportVOs.ExecutionQuery;
import com.erp.module.purchase.controller.vo.ReportVOs.ExecutionRow;
import com.erp.module.purchase.controller.vo.ReportVOs.PriceTrend;
import com.erp.module.purchase.controller.vo.ReportVOs.PriceTrendQuery;
import com.erp.module.purchase.controller.vo.ReportVOs.PriceTrendRow;
import com.erp.module.purchase.controller.vo.ReportVOs.SummaryQuery;
import com.erp.module.purchase.controller.vo.ReportVOs.SummaryRow;
import com.erp.module.purchase.controller.vo.ReportVOs.TrackingQuery;
import com.erp.module.purchase.controller.vo.ReportVOs.TrackingRow;
import com.erp.module.purchase.controller.vo.ReportVOs.TrendPoint;
import com.erp.module.purchase.controller.vo.ReportVOs.TrendSeries;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import com.erp.module.purchase.dal.dataobject.ReceiptDO;
import com.erp.module.purchase.dal.dataobject.ReceiptLineDO;
import com.erp.module.purchase.dal.dataobject.RequisitionLineDO;
import com.erp.module.purchase.dal.dataobject.ReturnDO;
import com.erp.module.purchase.dal.dataobject.ReturnLineDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.mapper.OrderLineMapper;
import com.erp.module.purchase.dal.mapper.OrderMapper;
import com.erp.module.purchase.dal.mapper.ReceiptLineMapper;
import com.erp.module.purchase.dal.mapper.ReceiptMapper;
import com.erp.module.purchase.dal.mapper.ReturnLineMapper;
import com.erp.module.purchase.dal.mapper.ReturnMapper;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.purchase.service.order.OrderService;
import com.erp.module.purchase.service.receipt.ReceiptService;
import com.erp.module.purchase.service.requisition.RequisitionService;
import com.erp.module.purchase.service.supplier.SupplierService;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 采购报表（需求 07-11）：交期跟踪 / 逾期未到货、订单执行表、价格趋势、采购汇总。按采购员数据权限过滤；金额需要 pur:price:view。
 */
@Service
public class ReportService {

    static final List<DocStatus> ACTIVE = List.of(DocStatus.APPROVED, DocStatus.IN_PROGRESS);
    static final List<DocStatus> EFFECTIVE = List.of(DocStatus.APPROVED, DocStatus.IN_PROGRESS, DocStatus.COMPLETED, DocStatus.CLOSED);
    static final Set<String> DIMS = Set.of("SUPPLIER", "CATEGORY", "MATERIAL", "BUYER", "MONTH");
    static final int MAX_ROWS = 20000;

    private final OrderMapper orderMapper;
    private final OrderLineMapper orderLineMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptLineMapper receiptLineMapper;
    private final ReturnMapper returnMapper;
    private final ReturnLineMapper returnLineMapper;
    private final SupplierService supplierService;
    private final RequisitionService requisitionService;
    private final PurSupport support;
    private final MaterialCategoryApi categoryApi;

    public ReportService(OrderMapper orderMapper, OrderLineMapper orderLineMapper, ReceiptMapper receiptMapper, ReceiptLineMapper receiptLineMapper,
                         ReturnMapper returnMapper, ReturnLineMapper returnLineMapper, SupplierService supplierService,
                         RequisitionService requisitionService, PurSupport support, MaterialCategoryApi categoryApi) {
        this.orderMapper = orderMapper;
        this.orderLineMapper = orderLineMapper;
        this.receiptMapper = receiptMapper;
        this.receiptLineMapper = receiptLineMapper;
        this.returnMapper = returnMapper;
        this.returnLineMapper = returnLineMapper;
        this.supplierService = supplierService;
        this.requisitionService = requisitionService;
        this.support = support;
        this.categoryApi = categoryApi;
    }

    /** 当前用户可见的订单（数据权限：采购员看自己的订单） */
    private List<OrderDO> visibleOrders(LambdaQueryWrapper<OrderDO> w) {
        return orderMapper.selectScopedList(w.eq(OrderDO::getDeleted, false).last("LIMIT " + MAX_ROWS));
    }

    // ==================== 交期跟踪 / 逾期未到货 ====================

    public PageResult<TrackingRow> tracking(TrackingQuery q) {
        List<TrackingRow> all = trackingRows(q);
        int from = Math.min((q.getPageNo() - 1) * q.getPageSize(), all.size());
        return new PageResult<>(all.subList(from, Math.min(from + q.getPageSize(), all.size())), all.size());
    }

    public List<TrackingRow> trackingRows(TrackingQuery q) {
        List<OrderDO> orders = visibleOrders(new LambdaQueryWrapper<OrderDO>().in(OrderDO::getStatus, ACTIVE)
                .eq(q.getSupplierId() != null, OrderDO::getSupplierId, q.getSupplierId()).eq(q.getOwnerId() != null, OrderDO::getOwnerId, q.getOwnerId()));
        if (orders.isEmpty()) return List.of();
        Map<Long, OrderDO> byId = orders.stream().collect(Collectors.toMap(OrderDO::getId, o -> o));
        LocalDate today = LocalDate.now();
        List<OrderLineDO> lines = orderLineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>().in(OrderLineDO::getOrderId, byId.keySet())
                .eq(OrderLineDO::getLineStatus, OrderService.OPEN).eq(q.getMaterialId() != null, OrderLineDO::getMaterialId, q.getMaterialId())
                .ge(q.getRequiredFrom() != null, OrderLineDO::getRequiredDate, q.getRequiredFrom())
                .le(q.getRequiredTo() != null, OrderLineDO::getRequiredDate, q.getRequiredTo())
                .ge(q.getConfirmedFrom() != null, OrderLineDO::getConfirmedDate, q.getConfirmedFrom())
                .le(q.getConfirmedTo() != null, OrderLineDO::getConfirmedDate, q.getConfirmedTo())
                .isNull(Boolean.TRUE.equals(q.getUnconfirmedOnly()), OrderLineDO::getConfirmedDate)).stream()
                .filter(l -> l.getReceivedQty().compareTo(l.getBaseQty()) < 0)
                .filter(l -> !Boolean.TRUE.equals(q.getOverdueOnly()) || OrderService.dueDate(l).isBefore(today))
                .sorted(Comparator.comparing(OrderService::dueDate).thenComparing(OrderLineDO::getId)).toList();
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(OrderLineDO::getMaterialId).toList());
        Map<Long, SupplierDO> ss = supplierService.byIds(orders.stream().map(OrderDO::getSupplierId).toList());
        Map<Long, UserDTO> users = support.users(orders.stream().map(OrderDO::getOwnerId).toList());
        Map<Long, RequisitionLineDO> reqs = requisitionService.lines(lines.stream().map(OrderLineDO::getRequisitionLineId).toList());
        return lines.stream().map(l -> {
            OrderDO o = byId.get(l.getOrderId());
            MaterialDTO m = ms.get(l.getMaterialId());
            SupplierDO s = ss.get(o.getSupplierId());
            RequisitionLineDO rl = reqs.get(l.getRequisitionLineId());
            return new TrackingRow(l.getId(), o.getId(), o.getDocNo(), l.getLineNo(), o.getSupplierId(), s == null ? null : s.getShortName(),
                    l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(),
                    l.getBaseQty(), l.getReceivedQty(), l.getBaseQty().subtract(l.getReceivedQty()), l.getRequiredDate(), l.getConfirmedDate(),
                    Math.max(0, ChronoUnit.DAYS.between(OrderService.dueDate(l), today)), rl == null ? null : rl.getSourceDemand(), o.getOwnerId(),
                    PurSupport.name(users, o.getOwnerId()), l.getLastFollowUp(), l.getFollowUpAt());
        }).toList();
    }

    // ==================== 订单执行表 ====================

    public PageResult<ExecutionRow> execution(ExecutionQuery q) {
        List<ExecutionRow> all = executionRows(q);
        int from = Math.min((q.getPageNo() - 1) * q.getPageSize(), all.size());
        return new PageResult<>(all.subList(from, Math.min(from + q.getPageSize(), all.size())), all.size());
    }

    public List<ExecutionRow> executionRows(ExecutionQuery q) {
        LambdaQueryWrapper<OrderDO> w = new LambdaQueryWrapper<OrderDO>()
                .likeRight(StringUtils.hasText(q.getDocNo()), OrderDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getSupplierId() != null, OrderDO::getSupplierId, q.getSupplierId()).eq(q.getOwnerId() != null, OrderDO::getOwnerId, q.getOwnerId())
                .ge(q.getDateFrom() != null, OrderDO::getDocDate, q.getDateFrom()).le(q.getDateTo() != null, OrderDO::getDocDate, q.getDateTo())
                .orderByDesc(OrderDO::getDocDate).orderByDesc(OrderDO::getId);
        w.in(OrderDO::getStatus, StringUtils.hasText(q.getStatuses())
                ? Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList() : EFFECTIVE);
        List<OrderDO> orders = visibleOrders(w);
        if (orders.isEmpty()) return List.of();
        Map<Long, List<OrderLineDO>> lines = orderLineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>()
                        .in(OrderLineDO::getOrderId, orders.stream().map(OrderDO::getId).toList())
                        .eq(q.getMaterialId() != null, OrderLineDO::getMaterialId, q.getMaterialId()).orderByAsc(OrderLineDO::getLineNo))
                .stream().collect(Collectors.groupingBy(OrderLineDO::getOrderId));
        Map<Long, MaterialDTO> ms = support.materials(lines.values().stream().flatMap(List::stream).map(OrderLineDO::getMaterialId).toList());
        Map<Long, SupplierDO> ss = supplierService.byIds(orders.stream().map(OrderDO::getSupplierId).toList());
        boolean price = PurSupport.canViewPrice();
        List<ExecutionRow> rows = new ArrayList<>();
        for (OrderDO o : orders) {
            SupplierDO s = ss.get(o.getSupplierId());
            for (OrderLineDO l : lines.getOrDefault(o.getId(), List.of())) {
                MaterialDTO m = ms.get(l.getMaterialId());
                rows.add(new ExecutionRow(o.getId(), o.getDocNo(), o.getDocDate(), o.getSupplierId(), s == null ? null : s.getShortName(), l.getLineNo(),
                        l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.baseUom(), l.getBaseQty(),
                        l.getReceivedQty(), l.getStockedQty(), l.getQualifiedQty(), l.getReturnedQty(), l.getStatementQty(),
                        OrderService.CLOSED.equals(l.getLineStatus()) ? BigDecimal.ZERO : l.getBaseQty().subtract(l.getReceivedQty()).max(BigDecimal.ZERO),
                        o.getCurrency(), PurSupport.mask(ReceiptService.basePriceInclTax(l), price), PurSupport.mask(l.getTotalAmount(), price),
                        l.getLineStatus()));
            }
        }
        return rows;
    }

    // ==================== 价格趋势 ====================

    /** 每月实际采购平均单价（按到货合格数量加权，折算本位币不含税，每基本单位） */
    public PriceTrend priceTrend(PriceTrendQuery q) {
        List<Long> materialIds = q.getMaterialIds() == null ? List.of() : Arrays.stream(q.getMaterialIds().split(",")).map(String::trim)
                .filter(x -> x.matches("\\d+")).map(Long::valueOf).distinct().toList();
        if (materialIds.size() > 5) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "最多选择 5 个物料");
        LocalDate to = q.getDateTo() != null ? q.getDateTo() : LocalDate.now();
        LocalDate from = q.getDateFrom() != null ? q.getDateFrom() : YearMonth.from(to).minusMonths(11).atDay(1);
        List<String> months = new ArrayList<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) months.add(m.toString());
        if (materialIds.isEmpty() || !PurSupport.canViewPrice()) return new PriceTrend(months, List.of(), List.of());
        List<ReceiptLineDO> lines = receiptLineMapper.selectList(new LambdaQueryWrapper<ReceiptLineDO>().in(ReceiptLineDO::getMaterialId, materialIds)
                .isNotNull(ReceiptLineDO::getOrderLineId).gt(ReceiptLineDO::getQualifiedQty, 0)
                .ge(ReceiptLineDO::getJudgedDate, from).le(ReceiptLineDO::getJudgedDate, to));
        Map<Long, ReceiptDO> receipts = lines.isEmpty() ? Collections.emptyMap() : receiptMapper.selectBatchIds(lines.stream().map(ReceiptLineDO::getReceiptId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(ReceiptDO::getId, r -> r));
        Map<Long, OrderLineDO> ols = lines.isEmpty() ? Collections.emptyMap() : orderLineMapper.selectBatchIds(lines.stream().map(ReceiptLineDO::getOrderLineId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(OrderLineDO::getId, l -> l));
        Map<Long, OrderDO> orders = ols.isEmpty() ? Collections.emptyMap() : orderMapper.selectBatchIds(ols.values().stream().map(OrderLineDO::getOrderId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(OrderDO::getId, o -> o));
        Map<String, Agg> byKey = new TreeMap<>();
        Map<String, Agg> byMonth = new HashMap<>();
        for (ReceiptLineDO l : lines) {
            ReceiptDO r = receipts.get(l.getReceiptId());
            OrderLineDO ol = ols.get(l.getOrderLineId());
            OrderDO o = ol == null ? null : orders.get(ol.getOrderId());
            if (r == null || o == null || (r.getStatus() != DocStatus.APPROVED && r.getStatus() != DocStatus.COMPLETED)) continue;
            BigDecimal unit = ol.getBaseQty().signum() == 0 ? ol.getPrice()
                    : ol.getPrice().multiply(ol.getQty()).divide(ol.getBaseQty(), 10, RoundingMode.HALF_UP);
            unit = unit.multiply(o.getExchangeRate());
            String month = YearMonth.from(l.getJudgedDate()).toString();
            byKey.computeIfAbsent(month + "|" + l.getMaterialId() + "|" + r.getSupplierId(), k -> new Agg()).add(l.getQualifiedQty(), unit);
            byMonth.computeIfAbsent(month + "|" + l.getMaterialId(), k -> new Agg()).add(l.getQualifiedQty(), unit);
        }
        Map<Long, MaterialDTO> ms = support.materials(materialIds);
        Map<Long, SupplierDO> ss = supplierService.byIds(receipts.values().stream().map(ReceiptDO::getSupplierId).toList());
        List<PriceTrendRow> rows = byKey.entrySet().stream().map(e -> {
            String[] k = e.getKey().split("\\|");
            MaterialDTO m = ms.get(Long.valueOf(k[1]));
            SupplierDO s = ss.get(Long.valueOf(k[2]));
            Agg a = e.getValue();
            return new PriceTrendRow(k[0], Long.valueOf(k[1]), m == null ? null : m.code(), m == null ? null : m.name(), Long.valueOf(k[2]),
                    s == null ? null : s.getShortName(), a.qty, a.avg(), a.max, a.min);
        }).toList();
        List<TrendSeries> series = materialIds.stream().map(mid -> {
            MaterialDTO m = ms.get(mid);
            return new TrendSeries(mid, m == null ? null : m.code(), m == null ? null : m.name(), months.stream()
                    .map(mo -> new TrendPoint(mo, byMonth.containsKey(mo + "|" + mid) ? byMonth.get(mo + "|" + mid).avg() : null)).toList());
        }).toList();
        return new PriceTrend(months, series, rows);
    }

    static final class Agg {
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal max;
        BigDecimal min;

        void add(BigDecimal q, BigDecimal unit) {
            qty = qty.add(q);
            amount = amount.add(q.multiply(unit));
            max = max == null || unit.compareTo(max) > 0 ? unit : max;
            min = min == null || unit.compareTo(min) < 0 ? unit : min;
            max = Decimals.price(max);
            min = Decimals.price(min);
        }

        BigDecimal avg() {
            return qty.signum() == 0 ? null : amount.divide(qty, Decimals.PRICE_SCALE, RoundingMode.HALF_UP);
        }
    }

    // ==================== 采购汇总 ====================

    /** 维度：供应商 / 物料类别 / 物料 / 采购员 / 月份（可选两个维度交叉）；金额为本位币 */
    public List<SummaryRow> summary(SummaryQuery q) {
        String d1 = StringUtils.hasText(q.getDim1()) ? q.getDim1() : "SUPPLIER";
        String d2 = StringUtils.hasText(q.getDim2()) ? q.getDim2() : null;
        if (d1 == null || !DIMS.contains(d1) || (d2 != null && (!DIMS.contains(d2) || d2.equals(d1)))) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "汇总维度");
        LocalDate to = q.getDateTo() != null ? q.getDateTo() : LocalDate.now();
        LocalDate from = q.getDateFrom() != null ? q.getDateFrom() : to.withDayOfYear(1);
        boolean price = PurSupport.canViewPrice();
        int tolerance = support.params().getInt(PurchaseModuleConfig.P_ONTIME_TOLERANCE);

        List<OrderDO> orders = visibleOrders(new LambdaQueryWrapper<OrderDO>().in(OrderDO::getStatus, EFFECTIVE));
        Map<Long, OrderDO> orderById = orders.stream().collect(Collectors.toMap(OrderDO::getId, o -> o));
        List<OrderLineDO> orderLines = orders.isEmpty() ? List.of()
                : orderLineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>().in(OrderLineDO::getOrderId, orderById.keySet()));
        Map<Long, OrderLineDO> olById = orderLines.stream().collect(Collectors.toMap(OrderLineDO::getId, l -> l));
        Map<Long, MaterialDTO> ms = support.materials(orderLines.stream().map(OrderLineDO::getMaterialId).toList());
        Map<String, Acc> acc = new LinkedHashMap<>();

        // 下单金额、订单行数：按订单日期
        for (OrderLineDO l : orderLines) {
            OrderDO o = orderById.get(l.getOrderId());
            if (o.getDocDate().isBefore(from) || o.getDocDate().isAfter(to)) continue;
            Acc a = acc(acc, d1, d2, o, l, ms.get(l.getMaterialId()), o.getDocDate());
            a.order = a.order.add(l.getTotalAmount().multiply(o.getExchangeRate()));
            a.lines++;
        }
        // 准时率：应到货日期在区间内
        for (OrderLineDO l : orderLines) {
            LocalDate due = OrderService.dueDate(l);
            if (due.isBefore(from) || due.isAfter(to) || (OrderService.CLOSED.equals(l.getLineStatus()) && l.getReceivedQty().signum() == 0)) continue;
            OrderDO o = orderById.get(l.getOrderId());
            Acc a = acc(acc, d1, d2, o, l, ms.get(l.getMaterialId()), due);
            a.due++;
            if (l.getFirstReceivedDate() != null && !l.getFirstReceivedDate().isAfter(due.plusDays(tolerance))
                    && l.getReceivedQty().compareTo(l.getBaseQty().multiply(new BigDecimal("0.95"))) >= 0) a.ontime++;
        }
        // 到货、合格金额与批次合格率：按到货时间 / 判定日期
        if (!olById.isEmpty()) {
            List<ReceiptLineDO> rls = receiptLineMapper.selectList(new LambdaQueryWrapper<ReceiptLineDO>().in(ReceiptLineDO::getOrderLineId, olById.keySet()));
            Map<Long, ReceiptDO> receipts = rls.isEmpty() ? Collections.emptyMap() : receiptMapper.selectBatchIds(rls.stream().map(ReceiptLineDO::getReceiptId)
                    .collect(Collectors.toSet())).stream().collect(Collectors.toMap(ReceiptDO::getId, r -> r));
            for (ReceiptLineDO rl : rls) {
                ReceiptDO r = receipts.get(rl.getReceiptId());
                if (r == null || (r.getStatus() != DocStatus.APPROVED && r.getStatus() != DocStatus.COMPLETED)) continue;
                OrderLineDO ol = olById.get(rl.getOrderLineId());
                OrderDO o = orderById.get(ol.getOrderId());
                BigDecimal unit = ReceiptService.basePriceInclTax(ol).multiply(o.getExchangeRate());
                LocalDate arrival = r.getArrivalAt().toLocalDate();
                if (!arrival.isBefore(from) && !arrival.isAfter(to)) {
                    Acc a = acc(acc, d1, d2, o, ol, ms.get(ol.getMaterialId()), arrival);
                    a.received = a.received.add(rl.getBaseQty().multiply(unit));
                }
                if (rl.getJudgedDate() != null && !rl.getJudgedDate().isBefore(from) && !rl.getJudgedDate().isAfter(to)) {
                    Acc a = acc(acc, d1, d2, o, ol, ms.get(ol.getMaterialId()), rl.getJudgedDate());
                    a.qualified = a.qualified.add(rl.getQualifiedQty().multiply(unit));
                    if (Boolean.TRUE.equals(rl.getInspectRequired()) && !ReceiptService.PENDING.equals(rl.getInspectStatus())) {
                        a.lots++;
                        if ("QUALIFIED".equals(rl.getInspectStatus())) a.passLots++;
                    }
                }
            }
            // 退货金额：按出库日期
            List<ReturnLineDO> rts = returnLineMapper.selectList(new LambdaQueryWrapper<ReturnLineDO>().in(ReturnLineDO::getOrderLineId, olById.keySet())
                    .gt(ReturnLineDO::getOutQty, 0).ge(ReturnLineDO::getOutDate, from).le(ReturnLineDO::getOutDate, to));
            Map<Long, ReturnDO> returns = rts.isEmpty() ? Collections.emptyMap() : returnMapper.selectBatchIds(rts.stream().map(ReturnLineDO::getReturnId)
                    .collect(Collectors.toSet())).stream().collect(Collectors.toMap(ReturnDO::getId, r -> r));
            for (ReturnLineDO rt : rts) {
                ReturnDO r = returns.get(rt.getReturnId());
                OrderLineDO ol = olById.get(rt.getOrderLineId());
                if (r == null || ol == null) continue;
                OrderDO o = orderById.get(ol.getOrderId());
                Acc a = acc(acc, d1, d2, o, ol, ms.get(ol.getMaterialId()), rt.getOutDate());
                a.returned = a.returned.add(rt.getOutQty().multiply(rt.getPriceInclTax()).multiply(r.getExchangeRate()));
            }
        }
        Map<String, String> labels = labels(acc.values(), ms);
        return acc.values().stream().sorted(Comparator.comparing((Acc a) -> a.order).reversed().thenComparing(a -> a.key1))
                .map(a -> new SummaryRow(a.key1, labels.getOrDefault(a.dim1 + ":" + a.key1, a.key1), a.key2,
                        a.key2 == null ? null : labels.getOrDefault(a.dim2 + ":" + a.key2, a.key2), PurSupport.mask(Decimals.amount(a.order), price),
                        PurSupport.mask(Decimals.amount(a.received), price), PurSupport.mask(Decimals.amount(a.qualified), price),
                        PurSupport.mask(Decimals.amount(a.returned), price), a.lines, rate(a.ontime, a.due), rate(a.passLots, a.lots))).toList();
    }

    static final class Acc {
        String dim1;
        String key1;
        String dim2;
        String key2;
        BigDecimal order = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal qualified = BigDecimal.ZERO;
        BigDecimal returned = BigDecimal.ZERO;
        int lines;
        int due;
        int ontime;
        int lots;
        int passLots;
    }

    private static Acc acc(Map<String, Acc> map, String d1, String d2, OrderDO o, OrderLineDO l, MaterialDTO m, LocalDate date) {
        String k1 = key(d1, o, l, m, date);
        String k2 = d2 == null ? null : key(d2, o, l, m, date);
        return map.computeIfAbsent(k1 + "|" + k2, k -> {
            Acc a = new Acc();
            a.dim1 = d1;
            a.key1 = k1;
            a.dim2 = d2;
            a.key2 = k2;
            return a;
        });
    }

    private static String key(String dim, OrderDO o, OrderLineDO l, MaterialDTO m, LocalDate date) {
        return switch (dim) {
            case "SUPPLIER" -> String.valueOf(o.getSupplierId());
            case "CATEGORY" -> m == null || m.categoryId() == null ? "-" : String.valueOf(m.categoryId());
            case "MATERIAL" -> String.valueOf(l.getMaterialId());
            case "BUYER" -> String.valueOf(o.getOwnerId());
            default -> YearMonth.from(date).toString();
        };
    }

    private Map<String, String> labels(java.util.Collection<Acc> accs, Map<Long, MaterialDTO> ms) {
        Map<String, String> map = new HashMap<>();
        Function<String, Long> id = s -> s != null && s.matches("\\d+") ? Long.valueOf(s) : null;
        List<Long> suppliers = new ArrayList<>();
        List<Long> users = new ArrayList<>();
        for (Acc a : accs) {
            for (String[] dk : new String[][]{{a.dim1, a.key1}, {a.dim2, a.key2}}) {
                if (dk[0] == null || dk[1] == null) continue;
                if ("SUPPLIER".equals(dk[0])) suppliers.add(id.apply(dk[1]));
                if ("BUYER".equals(dk[0])) users.add(id.apply(dk[1]));
                if ("MATERIAL".equals(dk[0])) {
                    MaterialDTO m = ms.get(id.apply(dk[1]));
                    if (m != null) map.put("MATERIAL:" + dk[1], m.code() + " " + m.name());
                }
                if ("CATEGORY".equals(dk[0]) && id.apply(dk[1]) != null && !map.containsKey("CATEGORY:" + dk[1])) {
                    map.put("CATEGORY:" + dk[1], categoryApi.get(id.apply(dk[1])).map(MaterialCategoryDTO::name).orElse(dk[1]));
                }
            }
        }
        supplierService.byIds(suppliers.stream().filter(Objects::nonNull).toList()).forEach((k, v) -> map.put("SUPPLIER:" + k, v.getShortName()));
        support.users(users.stream().filter(Objects::nonNull).toList()).forEach((k, v) -> map.put("BUYER:" + k, v.realName()));
        return map;
    }

    static BigDecimal rate(int part, int total) {
        return total == 0 ? null : BigDecimal.valueOf(part).multiply(PurSupport.HUNDRED).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
