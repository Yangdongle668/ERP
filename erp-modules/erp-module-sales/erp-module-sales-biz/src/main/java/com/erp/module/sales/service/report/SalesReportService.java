package com.erp.module.sales.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.category.MaterialCategoryApi;
import com.erp.module.engineering.api.category.MaterialCategoryDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.inventory.api.stock.InventoryQueryApi;
import com.erp.module.inventory.api.stock.StockSummary;
import com.erp.module.sales.api.SalesErrorCodes;
import com.erp.module.sales.controller.vo.ReportVOs.CustomerRankRow;
import com.erp.module.sales.controller.vo.ReportVOs.NameValue;
import com.erp.module.sales.controller.vo.ReportVOs.OpenOrderQuery;
import com.erp.module.sales.controller.vo.ReportVOs.OpenOrderRow;
import com.erp.module.sales.controller.vo.ReportVOs.OrderTrace;
import com.erp.module.sales.controller.vo.ReportVOs.Performance;
import com.erp.module.sales.controller.vo.ReportVOs.PerformanceRow;
import com.erp.module.sales.controller.vo.ReportVOs.PeriodQuery;
import com.erp.module.sales.controller.vo.ReportVOs.QuoteSuccess;
import com.erp.module.sales.controller.vo.ReportVOs.QuoteSuccessRow;
import com.erp.module.sales.controller.vo.ReportVOs.TraceStep;
import com.erp.module.sales.controller.vo.ReportVOs.TrendPoint;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalOrderExecDO;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import com.erp.module.sales.dal.dataobject.SalQuotationDO;
import com.erp.module.sales.dal.dataobject.SalQuotationLineDO;
import com.erp.module.sales.dal.dataobject.SalRfqDO;
import com.erp.module.sales.dal.mapper.SalOrderExecMapper;
import com.erp.module.sales.dal.mapper.SalOrderLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.dal.mapper.SalQuotationLineMapper;
import com.erp.module.sales.dal.mapper.SalQuotationMapper;
import com.erp.module.sales.dal.mapper.SalRfqMapper;
import com.erp.module.sales.service.QuoteStatus;
import com.erp.module.sales.service.SalSupport;
import com.erp.module.sales.service.order.OrderExecService;
import com.erp.module.sales.service.order.OrderService;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** 销售报表（需求 04-08）：按业务员数据权限过滤，金额为本位币 */
@Service("salesReportService")
public class SalesReportService {

    static final List<DocStatus> EFFECTIVE = List.of(DocStatus.APPROVED, DocStatus.IN_PROGRESS, DocStatus.COMPLETED, DocStatus.CLOSED);

    private final SalOrderMapper orderMapper;
    private final SalOrderLineMapper lineMapper;
    private final SalOrderExecMapper execMapper;
    private final SalQuotationMapper quotationMapper;
    private final SalQuotationLineMapper quotationLineMapper;
    private final SalRfqMapper rfqMapper;
    private final SalSupport support;
    private final InventoryQueryApi inventoryQueryApi;
    private final MaterialCategoryApi categoryApi;

    public SalesReportService(SalOrderMapper orderMapper, SalOrderLineMapper lineMapper, SalOrderExecMapper execMapper, SalQuotationMapper quotationMapper,
                              SalQuotationLineMapper quotationLineMapper, SalRfqMapper rfqMapper, SalSupport support, InventoryQueryApi inventoryQueryApi,
                              MaterialCategoryApi categoryApi) {
        this.categoryApi = categoryApi;
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
        this.execMapper = execMapper;
        this.quotationMapper = quotationMapper;
        this.quotationLineMapper = quotationLineMapper;
        this.rfqMapper = rfqMapper;
        this.support = support;
        this.inventoryQueryApi = inventoryQueryApi;
    }

    // ==================== 未交订单（2.2） ====================

    public PageResult<OpenOrderRow> openOrders(OpenOrderQuery q) {
        List<OpenOrderRow> all = openOrderRows(q);
        int from = Math.min((q.getPageNo() - 1) * q.getPageSize(), all.size());
        return new PageResult<>(all.subList(from, Math.min(from + q.getPageSize(), all.size())), all.size());
    }

    public List<OpenOrderRow> openOrderRows(OpenOrderQuery q) {
        List<SalOrderDO> orders = orderMapper.selectScopedList(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getDeleted, false)
                .in(SalOrderDO::getStatus, OrderService.ACTIVE).eq(q.getCustomerId() != null, SalOrderDO::getCustomerId, q.getCustomerId())
                .eq(q.getOwnerId() != null, SalOrderDO::getOwnerId, q.getOwnerId())
                .likeRight(StringUtils.hasText(q.getOrderNo()), SalOrderDO::getDocNo, q.getOrderNo() == null ? null : q.getOrderNo().trim().toUpperCase()));
        if (orders.isEmpty()) return List.of();
        Map<Long, SalOrderDO> byId = orders.stream().collect(Collectors.toMap(SalOrderDO::getId, o -> o));
        List<SalOrderLineDO> lines = lineMapper.selectList(new LambdaQueryWrapper<SalOrderLineDO>().in(SalOrderLineDO::getOrderId, byId.keySet())
                .eq(SalOrderLineDO::getLineStatus, OrderService.OPEN).eq(q.getMaterialId() != null, SalOrderLineDO::getMaterialId, q.getMaterialId()))
                .stream().filter(l -> l.getBaseQty().compareTo(l.getShippedQty()) > 0).toList();
        LocalDate today = LocalDate.now();
        if (q.getDueWithinDays() != null) {
            LocalDate limit = today.plusDays(q.getDueWithinDays());
            lines = lines.stream().filter(l -> !OrderService.dueDate(l).isAfter(limit)).toList();
        }
        Set<Long> materialIds = lines.stream().map(SalOrderLineDO::getMaterialId).collect(Collectors.toSet());
        Map<Long, MaterialDTO> ms = support.materials(materialIds);
        Map<Long, StockSummary> stock = materialIds.isEmpty() ? Map.of() : inventoryQueryApi.getStockSummary(materialIds);
        Map<Long, CustomerDTO> cs = support.customers(orders.stream().map(SalOrderDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(orders.stream().map(SalOrderDO::getOwnerId).toList());
        return lines.stream().map(l -> {
            SalOrderDO o = byId.get(l.getOrderId());
            MaterialDTO m = ms.get(l.getMaterialId());
            StockSummary s = stock.get(l.getMaterialId());
            return new OpenOrderRow(o.getId(), o.getDocNo(), l.getId(), l.getLineNo(), o.getCustomerId(), SalSupport.shortName(cs, o.getCustomerId()),
                    l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(),
                    l.getBaseQty(), l.getShippedQty(), OrderService.openQty(l), s == null ? BigDecimal.ZERO : s.availableQty(), null, l.getRequiredDate(),
                    l.getPromisedDate(), (int) ChronoUnit.DAYS.between(today, OrderService.dueDate(l)), SalSupport.name(users, o.getOwnerId()));
        }).sorted(Comparator.comparingInt(OpenOrderRow::daysToDue).thenComparing(OpenOrderRow::orderNo).thenComparing(OpenOrderRow::lineNo)).toList();
    }

    // ==================== 订单执行跟踪（2.3） ====================

    public OrderTrace trace(Long orderLineId) {
        SalOrderLineDO l = lineMapper.selectById(orderLineId);
        if (l == null) throw new BizException(SalesErrorCodes.ORDER_LINE_NOT_EXISTS);
        SalOrderDO o = orderMapper.selectById(l.getOrderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        MaterialDTO m = support.material(l.getMaterialId());
        List<TraceStep> steps = new ArrayList<>();
        steps.add(new TraceStep("ORDER", "下单", o.getDocDate(), o.getCreatedAt(), o.getDocNo(), l.getBaseQty(), l.getTotalAmount(), null));
        if (o.getApprovedAt() != null) steps.add(new TraceStep("APPROVE", "审核", o.getApprovedAt().toLocalDate(), o.getApprovedAt(), o.getDocNo(), null, null, null));
        if (l.getPromisedDate() != null) {
            steps.add(new TraceStep("PROMISE", "PMC 承诺交期", l.getPromisedAt() == null ? null : l.getPromisedAt().toLocalDate(), l.getPromisedAt(), null, null,
                    null, "承诺交期 " + l.getPromisedDate() + (l.getPromiseRemark() == null ? "" : "（" + l.getPromiseRemark() + "）")));
        }
        List<SalOrderExecDO> execs = execMapper.selectList(new LambdaQueryWrapper<SalOrderExecDO>().eq(SalOrderExecDO::getOrderId, o.getId())
                .and(w -> w.eq(SalOrderExecDO::getOrderLineId, orderLineId).or().isNull(SalOrderExecDO::getOrderLineId)).orderByAsc(SalOrderExecDO::getId));
        for (SalOrderExecDO e : execs) {
            steps.add(new TraceStep(e.getExecType(), switch (e.getExecType()) {
                case OrderExecService.NOTICE -> "出货通知";
                case OrderExecService.SHIP -> "出库";
                case OrderExecService.SHIP_REVERSE -> "出库冲销";
                case OrderExecService.BL -> "提单";
                case OrderExecService.INVOICE -> "开票";
                case OrderExecService.RECEIPT -> "回款";
                default -> "退货";
            }, e.getExecDate(), e.getCreatedAt(), e.getDocNo(), e.getQty(), e.getAmount(), null));
        }
        if (o.getStatus() == DocStatus.CLOSED) steps.add(new TraceStep("CLOSE", "关闭", o.getUpdatedAt().toLocalDate(), o.getUpdatedAt(), null, null, null, o.getCloseReason()));
        return new OrderTrace(o.getId(), o.getDocNo(), l.getId(), l.getLineNo(), m.code(), m.name(), l.getBaseQty(), steps);
    }

    // ==================== 业务员业绩（2.4） ====================

    public Performance performance(PeriodQuery q) {
        LocalDate from = q.getFrom() != null ? q.getFrom() : LocalDate.now().withDayOfMonth(1);
        LocalDate to = q.getTo() != null ? q.getTo() : LocalDate.now();
        List<SalOrderDO> orders = scopedOrders(q);
        Map<Long, SalOrderDO> byId = orders.stream().collect(Collectors.toMap(SalOrderDO::getId, o -> o));
        Map<Long, BigDecimal> orderAmt = new HashMap<>();
        Map<Long, Integer> orderCnt = new HashMap<>();
        Map<String, BigDecimal[]> trend = new TreeMap<>();
        for (SalOrderDO o : orders) {
            if (o.getApprovedAt() == null || OrderService.REPLACEMENT.equals(o.getOrderType())) continue;
            LocalDate d = o.getApprovedAt().toLocalDate();
            if (d.isBefore(from) || d.isAfter(to)) continue;
            orderAmt.merge(o.getOwnerId(), o.getTotalAmountBase(), BigDecimal::add);
            orderCnt.merge(o.getOwnerId(), 1, Integer::sum);
            trend.computeIfAbsent(YearMonth.from(d).toString(), k -> zero3())[0] = trend.get(YearMonth.from(d).toString())[0].add(o.getTotalAmountBase());
        }
        Map<Long, BigDecimal> shipAmt = new HashMap<>();
        Map<Long, BigDecimal> receiptAmt = new HashMap<>();
        if (!byId.isEmpty()) {
            for (SalOrderExecDO e : execMapper.selectList(new LambdaQueryWrapper<SalOrderExecDO>().in(SalOrderExecDO::getOrderId, byId.keySet())
                    .in(SalOrderExecDO::getExecType, OrderExecService.SHIP, OrderExecService.SHIP_REVERSE, OrderExecService.RECEIPT)
                    .ge(SalOrderExecDO::getExecDate, from).le(SalOrderExecDO::getExecDate, to))) {
                SalOrderDO o = byId.get(e.getOrderId());
                BigDecimal base = SalSupport.nz(e.getAmount()).multiply(o.getExchangeRate());
                String month = YearMonth.from(e.getExecDate()).toString();
                if (OrderExecService.RECEIPT.equals(e.getExecType())) {
                    receiptAmt.merge(o.getOwnerId(), base, BigDecimal::add);
                    trend.computeIfAbsent(month, k -> zero3())[2] = trend.get(month)[2].add(base);
                } else {
                    shipAmt.merge(o.getOwnerId(), base, BigDecimal::add);
                    trend.computeIfAbsent(month, k -> zero3())[1] = trend.get(month)[1].add(base);
                }
            }
        }
        // 新客户：期内首次审核订单的客户（按全部历史订单判断）
        Map<Long, Integer> newCustomers = new HashMap<>();
        Set<Long> customers = orders.stream().map(SalOrderDO::getCustomerId).collect(Collectors.toSet());
        if (!customers.isEmpty()) {
            Map<Long, SalOrderDO> first = new HashMap<>();
            for (SalOrderDO o : orderMapper.selectList(new LambdaQueryWrapper<SalOrderDO>().in(SalOrderDO::getCustomerId, customers).isNotNull(SalOrderDO::getApprovedAt)
                    .in(SalOrderDO::getStatus, EFFECTIVE))) {
                first.merge(o.getCustomerId(), o, (a, b) -> a.getApprovedAt().isBefore(b.getApprovedAt()) ? a : b);
            }
            first.values().stream().filter(o -> byId.containsKey(o.getId())).filter(o -> !o.getApprovedAt().toLocalDate().isBefore(from)
                    && !o.getApprovedAt().toLocalDate().isAfter(to)).forEach(o -> newCustomers.merge(o.getOwnerId(), 1, Integer::sum));
        }
        Set<Long> owners = new HashSet<>();
        owners.addAll(orderAmt.keySet());
        owners.addAll(shipAmt.keySet());
        owners.addAll(receiptAmt.keySet());
        owners.remove(null);
        Map<Long, UserDTO> users = support.users(owners);
        Map<Long, OrgDTO> depts = support.orgs(users.values().stream().map(UserDTO::deptId).toList());
        List<PerformanceRow> rows = owners.stream().map(id -> {
            UserDTO u = users.get(id);
            OrgDTO dept = u == null ? null : depts.get(u.deptId());
            return new PerformanceRow(id, u == null ? null : u.realName(), dept == null ? null : dept.name(), scale(orderAmt.get(id)), scale(shipAmt.get(id)),
                    scale(receiptAmt.get(id)), null, newCustomers.getOrDefault(id, 0), orderCnt.getOrDefault(id, 0));
        }).sorted(Comparator.comparing(PerformanceRow::orderAmount).reversed()).toList();
        List<TrendPoint> points = trend.entrySet().stream().map(e -> new TrendPoint(e.getKey(), scale(e.getValue()[0]), scale(e.getValue()[1]),
                scale(e.getValue()[2]))).toList();
        return new Performance(support.baseCurrency(), rows, points);
    }

    private List<SalOrderDO> scopedOrders(PeriodQuery q) {
        return orderMapper.selectScopedList(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getDeleted, false).in(SalOrderDO::getStatus, EFFECTIVE)
                .eq(q.getOwnerId() != null, SalOrderDO::getOwnerId, q.getOwnerId()).eq(q.getDeptId() != null, SalOrderDO::getDeptId, q.getDeptId())
                .eq(q.getCustomerId() != null, SalOrderDO::getCustomerId, q.getCustomerId()));
    }

    private static BigDecimal[] zero3() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    // ==================== 报价成功率（2.5） ====================

    public QuoteSuccess quoteSuccess(PeriodQuery q) {
        LocalDate from = q.getFrom() != null ? q.getFrom() : LocalDate.now().minusMonths(3);
        LocalDate to = q.getTo() != null ? q.getTo() : LocalDate.now();
        // 修订链只统计最新版本；草稿、审批中不计
        List<SalQuotationDO> list = quotationMapper.selectScopedList(new LambdaQueryWrapper<SalQuotationDO>().eq(SalQuotationDO::getDeleted, false)
                .ge(SalQuotationDO::getDocDate, from).le(SalQuotationDO::getDocDate, to)
                .notIn(SalQuotationDO::getQuoteStatus, QuoteStatus.DRAFT.name(), QuoteStatus.PENDING.name(), QuoteStatus.REVISED.name())
                .eq(q.getOwnerId() != null, SalQuotationDO::getOwnerId, q.getOwnerId()).eq(q.getCustomerId() != null, SalQuotationDO::getCustomerId, q.getCustomerId()));
        String groupBy = StringUtils.hasText(q.getGroupBy()) ? q.getGroupBy() : "OWNER";
        Map<String, List<SalQuotationDO>> groups = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        Map<Long, UserDTO> users = support.users(list.stream().map(SalQuotationDO::getOwnerId).toList());
        Map<Long, CustomerDTO> cs = support.customers(list.stream().map(SalQuotationDO::getCustomerId).toList());
        Map<Long, Long> categoryOf = new HashMap<>();
        if ("CATEGORY".equals(groupBy) && !list.isEmpty()) {
            Map<Long, List<SalQuotationLineDO>> lines = quotationLineMapper.selectByParents(list.stream().map(SalQuotationDO::getId).toList()).stream()
                    .collect(Collectors.groupingBy(SalQuotationLineDO::getQuotationId));
            Map<Long, MaterialDTO> ms = support.materials(lines.values().stream().flatMap(List::stream).map(SalQuotationLineDO::getMaterialId).toList());
            lines.forEach((qid, ls) -> ls.stream().map(l -> ms.get(l.getMaterialId())).filter(Objects::nonNull).findFirst()
                    .ifPresent(m -> categoryOf.put(qid, m.categoryId())));
        }
        for (SalQuotationDO x : list) {
            String key;
            String name;
            switch (groupBy) {
                case "CUSTOMER" -> {
                    key = String.valueOf(x.getCustomerId());
                    name = SalSupport.shortName(cs, x.getCustomerId());
                }
                case "CATEGORY" -> {
                    Long cat = categoryOf.get(x.getId());
                    key = String.valueOf(cat);
                    name = cat == null ? "未分类" : categoryApi.get(cat).map(MaterialCategoryDTO::name).orElse(String.valueOf(cat));
                }
                default -> {
                    key = String.valueOf(x.getOwnerId());
                    name = SalSupport.name(users, x.getOwnerId());
                }
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(x);
            names.put(key, name);
        }
        Map<Long, SalRfqDO> rfqs = list.stream().map(SalQuotationDO::getRfqId).filter(Objects::nonNull).distinct().map(rfqMapper::selectById)
                .filter(Objects::nonNull).collect(Collectors.toMap(SalRfqDO::getId, r -> r));
        List<QuoteSuccessRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<SalQuotationDO>> e : groups.entrySet()) {
            List<SalQuotationDO> qs = e.getValue();
            int won = (int) qs.stream().filter(x -> QuoteStatus.WON.name().equals(x.getQuoteStatus())).count();
            int lost = (int) qs.stream().filter(x -> QuoteStatus.LOST.name().equals(x.getQuoteStatus())).count();
            List<Long> cycles = qs.stream().filter(x -> x.getRfqId() != null && rfqs.containsKey(x.getRfqId()))
                    .map(x -> ChronoUnit.DAYS.between(rfqs.get(x.getRfqId()).getDocDate(), x.getDocDate())).toList();
            BigDecimal avg = cycles.isEmpty() ? null : BigDecimal.valueOf(cycles.stream().mapToLong(Long::longValue).average().orElse(0)).setScale(1, RoundingMode.HALF_UP);
            rows.add(new QuoteSuccessRow(e.getKey(), names.get(e.getKey()), qs.size(), won, lost, rate(won, qs.size()), avg));
        }
        rows.sort(Comparator.comparingInt(QuoteSuccessRow::quoteCount).reversed());
        Map<String, Long> reasons = list.stream().filter(x -> QuoteStatus.LOST.name().equals(x.getQuoteStatus()) && x.getLostReason() != null)
                .collect(Collectors.groupingBy(SalQuotationDO::getLostReason, LinkedHashMap::new, Collectors.counting()));
        List<NameValue> lost = reasons.entrySet().stream().map(e -> new NameValue(e.getKey(),
                Objects.toString(support.dict().label("sal_quote_lost_reason", e.getKey()), e.getKey()), BigDecimal.valueOf(e.getValue()))).toList();
        int won = (int) list.stream().filter(x -> QuoteStatus.WON.name().equals(x.getQuoteStatus())).count();
        return new QuoteSuccess(list.size(), won, rate(won, list.size()), rows, lost);
    }

    private static BigDecimal rate(int part, int total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    // ==================== 客户销售排名（2.6） ====================

    public List<CustomerRankRow> customerRanking(PeriodQuery q) {
        LocalDate from = q.getFrom() != null ? q.getFrom() : LocalDate.now().withDayOfYear(1);
        LocalDate to = q.getTo() != null ? q.getTo() : LocalDate.now();
        List<SalOrderDO> orders = scopedOrders(q);
        Map<Long, BigDecimal> amt = new HashMap<>();
        Map<Long, BigDecimal> lastYear = new HashMap<>();
        for (SalOrderDO o : orders) {
            if (o.getApprovedAt() == null || OrderService.REPLACEMENT.equals(o.getOrderType())) continue;
            LocalDate d = o.getApprovedAt().toLocalDate();
            if (!d.isBefore(from) && !d.isAfter(to)) amt.merge(o.getCustomerId(), o.getTotalAmountBase(), BigDecimal::add);
            if (!d.isBefore(from.minusYears(1)) && !d.isAfter(to.minusYears(1))) lastYear.merge(o.getCustomerId(), o.getTotalAmountBase(), BigDecimal::add);
        }
        Map<Long, SalOrderDO> byId = orders.stream().collect(Collectors.toMap(SalOrderDO::getId, o -> o));
        Map<Long, BigDecimal> ship = new HashMap<>();
        if (!byId.isEmpty()) {
            for (SalOrderExecDO e : execMapper.selectList(new LambdaQueryWrapper<SalOrderExecDO>().in(SalOrderExecDO::getOrderId, byId.keySet())
                    .in(SalOrderExecDO::getExecType, OrderExecService.SHIP, OrderExecService.SHIP_REVERSE).ge(SalOrderExecDO::getExecDate, from)
                    .le(SalOrderExecDO::getExecDate, to))) {
                SalOrderDO o = byId.get(e.getOrderId());
                ship.merge(o.getCustomerId(), SalSupport.nz(e.getAmount()).multiply(o.getExchangeRate()), BigDecimal::add);
            }
        }
        BigDecimal total = SalSupport.sum(amt.values());
        Map<Long, CustomerDTO> cs = support.customers(amt.keySet());
        List<Map.Entry<Long, BigDecimal>> sorted = amt.entrySet().stream().sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed()).toList();
        List<CustomerRankRow> rows = new ArrayList<>();
        BigDecimal cum = BigDecimal.ZERO;
        int rank = 0;
        for (Map.Entry<Long, BigDecimal> e : sorted) {
            rank++;
            CustomerDTO c = cs.get(e.getKey());
            BigDecimal share = total.signum() == 0 ? BigDecimal.ZERO : e.getValue().divide(total, 4, RoundingMode.HALF_UP);
            BigDecimal before = cum;
            cum = cum.add(share);
            String abc = before.compareTo(new BigDecimal("0.80")) < 0 ? "A" : before.compareTo(new BigDecimal("0.95")) < 0 ? "B" : "C";
            BigDecimal ly = lastYear.get(e.getKey());
            BigDecimal growth = ly == null || ly.signum() == 0 ? null : e.getValue().subtract(ly).divide(ly, 4, RoundingMode.HALF_UP);
            rows.add(new CustomerRankRow(rank, e.getKey(), c == null ? null : c.code(), c == null ? null : c.shortName(), scale(e.getValue()),
                    scale(ship.get(e.getKey())), share, ly == null ? null : scale(ly), growth, abc));
        }
        return rows;
    }
}
