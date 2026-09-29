package com.erp.module.shipping.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.crm.api.customer.AddressDTO;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.inventory.api.stock.InventoryQueryApi;
import com.erp.module.sales.api.order.OpenLineFilter;
import com.erp.module.sales.api.order.SalesOrderHeaderDTO;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.shipping.config.ShippingModuleConfig;
import com.erp.module.shipping.controller.vo.ReportVOs.DelayRow;
import com.erp.module.shipping.controller.vo.ReportVOs.DetailReport;
import com.erp.module.shipping.controller.vo.ReportVOs.DetailRow;
import com.erp.module.shipping.controller.vo.ReportVOs.ExportStatRow;
import com.erp.module.shipping.controller.vo.ReportVOs.OnTimeGroup;
import com.erp.module.shipping.controller.vo.ReportVOs.OnTimeReport;
import com.erp.module.shipping.controller.vo.ReportVOs.PendingRow;
import com.erp.module.shipping.controller.vo.ReportVOs.ReportQuery;
import com.erp.module.shipping.controller.vo.ReportVOs.SummaryRow;
import com.erp.module.shipping.dal.dataobject.ShpNoticeDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeLineDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentLineDO;
import com.erp.module.shipping.dal.mapper.ShpNoticeLineMapper;
import com.erp.module.shipping.dal.mapper.ShpNoticeMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentLineMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentMapper;
import com.erp.module.shipping.service.NoticeStatus;
import com.erp.module.shipping.service.ShipmentStatus;
import com.erp.module.shipping.service.ShpSupport;
import com.erp.module.shipping.service.notice.NoticeFlow;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 出货报表（11-06）：待出货、出货明细、准时率、出口统计 */
@Service
public class ShippingReportService {

    static final List<String> SHIPPED = List.of(ShipmentStatus.SHIPPED.name(), ShipmentStatus.COMPLETED.name());
    static final List<String> NOTICE_ACTIVE = List.of(NoticeStatus.APPROVED.name(), NoticeStatus.PICKING.name(), NoticeStatus.PACKED.name(),
            NoticeStatus.OQC.name(), NoticeStatus.READY.name());

    private final ShpNoticeMapper noticeMapper;
    private final ShpNoticeLineMapper noticeLineMapper;
    private final ShpShipmentMapper shipmentMapper;
    private final ShpShipmentLineMapper shipmentLineMapper;
    private final SalesOrderQueryApi orderQueryApi;
    private final InventoryQueryApi inventoryQueryApi;
    private final ShpSupport support;

    public ShippingReportService(ShpNoticeMapper noticeMapper, ShpNoticeLineMapper noticeLineMapper, ShpShipmentMapper shipmentMapper,
                                 ShpShipmentLineMapper shipmentLineMapper, SalesOrderQueryApi orderQueryApi, InventoryQueryApi inventoryQueryApi,
                                 ShpSupport support) {
        this.noticeMapper = noticeMapper;
        this.noticeLineMapper = noticeLineMapper;
        this.shipmentMapper = shipmentMapper;
        this.shipmentLineMapper = shipmentLineMapper;
        this.orderQueryApi = orderQueryApi;
        this.inventoryQueryApi = inventoryQueryApi;
        this.support = support;
    }

    // ==================== 待出货清单 ====================

    public List<PendingRow> pending(ReportQuery q) {
        int days = q.getDays() != null && q.getDays() > 0 ? q.getDays() : Math.max(1, support.params().getInt(ShippingModuleConfig.P_PENDING_DAYS));
        LocalDate today = LocalDate.now();
        List<ShpNoticeDO> notices = noticeMapper.selectList(new LambdaQueryWrapper<ShpNoticeDO>().in(ShpNoticeDO::getNoticeStatus, NOTICE_ACTIVE)
                .eq(q.getCustomerId() != null, ShpNoticeDO::getCustomerId, q.getCustomerId()));
        Map<Long, ShpNoticeDO> nById = notices.stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
        List<ShpNoticeLineDO> nls = noticeLineMapper.selectByParents(nById.keySet()).stream()
                .filter(l -> q.getMaterialId() == null || q.getMaterialId().equals(l.getMaterialId()))
                .filter(l -> NoticeFlow.effectiveQty(l).compareTo(ShpSupport.nz(l.getShippedQty())) > 0).toList();
        List<SalesOrderLineDTO> open = orderQueryApi.getOpenLines(new OpenLineFilter(q.getCustomerId(), q.getMaterialId(), null, q.getOwnerId(),
                today.plusDays(days)));
        Map<Long, SalesOrderLineDTO> ols = new HashMap<>(orderQueryApi.getLines(nls.stream().map(ShpNoticeLineDO::getOrderLineId).toList()));
        open.forEach(l -> ols.putIfAbsent(l.lineId(), l));
        List<Long> materialIds = new ArrayList<>(nls.stream().map(ShpNoticeLineDO::getMaterialId).toList());
        materialIds.addAll(open.stream().map(SalesOrderLineDTO::materialId).toList());
        Map<Long, MaterialDTO> ms = support.materials(materialIds);
        List<Long> customerIds = new ArrayList<>(notices.stream().map(ShpNoticeDO::getCustomerId).toList());
        customerIds.addAll(open.stream().map(SalesOrderLineDTO::customerId).toList());
        Map<Long, CustomerDTO> cus = support.customers(customerIds);
        Map<Long, UserDTO> users = support.users(ols.values().stream().map(SalesOrderLineDTO::ownerId).toList());
        Map<Long, BigDecimal> avail = new HashMap<>();
        List<PendingRow> rows = new ArrayList<>();
        for (ShpNoticeLineDO l : nls) {
            ShpNoticeDO n = nById.get(l.getNoticeId());
            SalesOrderLineDTO ol = ols.get(l.getOrderLineId());
            if (q.getOwnerId() != null && (ol == null || !q.getOwnerId().equals(ol.ownerId()))) continue;
            MaterialDTO m = ms.get(l.getMaterialId());
            LocalDate due = ol == null ? n.getShipDate() : ol.dueDate();
            rows.add(new PendingRow(n.getCustomerId(), ShpSupport.customerName(cus.get(n.getCustomerId())), l.getOrderId(), l.getOrderNo(), l.getOrderLineNo(),
                    l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), NoticeFlow.effectiveQty(l).subtract(ShpSupport.nz(l.getShippedQty())),
                    due, due != null && due.isBefore(today), n.getId(), n.getDocNo(), n.getNoticeStatus(),
                    avail.computeIfAbsent(l.getMaterialId(), inventoryQueryApi::getAvailableQty), ol == null ? null : ol.ownerId(),
                    ol == null ? null : ShpSupport.name(users, ol.ownerId())));
        }
        for (SalesOrderLineDTO ol : open) {
            BigDecimal notNoticed = ol.baseQty().subtract(ShpSupport.nz(ol.noticedQty()));
            if (notNoticed.signum() <= 0) continue;
            MaterialDTO m = ms.get(ol.materialId());
            rows.add(new PendingRow(ol.customerId(), ShpSupport.customerName(cus.get(ol.customerId())), ol.orderId(), ol.orderNo(), ol.lineNo(), ol.materialId(),
                    m == null ? null : m.code(), m == null ? null : m.name(), notNoticed, ol.dueDate(), ol.dueDate() != null && ol.dueDate().isBefore(today),
                    null, null, null, avail.computeIfAbsent(ol.materialId(), inventoryQueryApi::getAvailableQty), ol.ownerId(), ShpSupport.name(users, ol.ownerId())));
        }
        rows.sort(Comparator.comparing(PendingRow::dueDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(PendingRow::orderNo,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    // ==================== 出货明细 ====================

    private List<ShpShipmentDO> shippedShipments(ReportQuery q) {
        return shipmentMapper.selectScopedList(new LambdaQueryWrapper<ShpShipmentDO>().eq(ShpShipmentDO::getDeleted, false)
                .in(ShpShipmentDO::getShipmentStatus, SHIPPED)
                .eq(q.getCustomerId() != null, ShpShipmentDO::getCustomerId, q.getCustomerId())
                .ge(q.getDateFrom() != null, ShpShipmentDO::getShipDate, q.getDateFrom())
                .le(q.getDateTo() != null, ShpShipmentDO::getShipDate, q.getDateTo())
                .orderByAsc(ShpShipmentDO::getShipDate).orderByAsc(ShpShipmentDO::getId));
    }

    public DetailReport details(ReportQuery q) {
        List<ShpShipmentDO> ships = shippedShipments(q);
        Map<Long, ShpShipmentDO> sById = ships.stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
        List<ShpShipmentLineDO> lines = shipmentLineMapper.selectByParents(sById.keySet()).stream()
                .filter(l -> q.getMaterialId() == null || q.getMaterialId().equals(l.getMaterialId())).toList();
        Map<Long, SalesOrderHeaderDTO> heads = orderQueryApi.getOrderHeaders(lines.stream().map(ShpShipmentLineDO::getOrderId).distinct().toList());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(ShpShipmentLineDO::getMaterialId).toList());
        Map<Long, CustomerDTO> cus = support.customers(ships.stream().map(ShpShipmentDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(heads.values().stream().map(SalesOrderHeaderDTO::ownerId).toList());
        boolean price = support.canSeePrice();
        List<DetailRow> rows = new ArrayList<>();
        for (ShpShipmentLineDO l : lines) {
            ShpShipmentDO s = sById.get(l.getShipmentId());
            SalesOrderHeaderDTO h = heads.get(l.getOrderId());
            if (q.getOwnerId() != null && (h == null || !q.getOwnerId().equals(h.ownerId()))) continue;
            MaterialDTO m = ms.get(l.getMaterialId());
            BigDecimal qty = ShpSupport.nz(l.getOutQty()).signum() > 0 ? l.getOutQty() : l.getBaseQty();
            BigDecimal base = support.currencyApi().toBase(ShpSupport.nz(l.getTotalAmount()), s.getExchangeRate()).setScale(2, RoundingMode.HALF_UP);
            rows.add(new DetailRow(s.getId(), s.getDocNo(), s.getShipDate(), s.getCustomerId(), ShpSupport.customerName(cus.get(s.getCustomerId())), l.getOrderId(),
                    l.getOrderNo(), h == null ? null : h.customerPoNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    l.getBatchNo(), qty, m == null ? l.getUom() : m.baseUom(), s.getCurrency(), price ? l.getPriceInclTax() : null,
                    price ? l.getTotalAmount() : null, price ? base : null, s.getTransportMode(), s.getBlNo(), h == null ? null : h.ownerId(),
                    h == null ? null : ShpSupport.name(users, h.ownerId())));
        }
        String group = q.getGroupBy() == null ? "CUSTOMER" : q.getGroupBy();
        Map<String, SummaryAcc> acc = new LinkedHashMap<>();
        for (DetailRow r : rows) {
            String key;
            String label;
            switch (group) {
                case "MATERIAL" -> {
                    key = String.valueOf(r.materialId());
                    label = r.materialCode() + " " + Objects.toString(r.materialName(), "");
                }
                case "MONTH" -> {
                    key = r.shipDate() == null ? "" : r.shipDate().toString().substring(0, 7);
                    label = key;
                }
                case "OWNER" -> {
                    key = String.valueOf(r.ownerId());
                    label = Objects.toString(r.ownerName(), "-");
                }
                default -> {
                    key = String.valueOf(r.customerId());
                    label = Objects.toString(r.customerName(), "-");
                }
            }
            acc.computeIfAbsent(key, k -> new SummaryAcc(label)).add(r.qty(), r.amountBase());
        }
        List<SummaryRow> summary = acc.entrySet().stream().map(e -> new SummaryRow(e.getKey(), e.getValue().label, e.getValue().lines, e.getValue().qty,
                price ? e.getValue().amount : null)).toList();
        BigDecimal totalQty = rows.stream().map(DetailRow::qty).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmt = rows.stream().map(DetailRow::amountBase).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DetailReport(rows, summary, totalQty, price ? totalAmt : null);
    }

    private static final class SummaryAcc {
        final String label;
        int lines;
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;

        SummaryAcc(String label) {
            this.label = label;
        }

        void add(BigDecimal q, BigDecimal a) {
            lines++;
            qty = qty.add(ShpSupport.nz(q));
            amount = amount.add(ShpSupport.nz(a));
        }
    }

    // ==================== 出货准时率 ====================

    /** 期间内首次出货的订单行：首次出货日期 ≤ 交期（承诺交期，无则要求交期）为按期（SHP-RPT-T01） */
    public OnTimeReport onTime(ReportQuery q) {
        LocalDate from = q.getDateFrom();
        LocalDate to = q.getDateTo();
        List<ShpShipmentDO> all = shipmentMapper.selectScopedList(new LambdaQueryWrapper<ShpShipmentDO>().eq(ShpShipmentDO::getDeleted, false)
                .in(ShpShipmentDO::getShipmentStatus, SHIPPED).eq(q.getCustomerId() != null, ShpShipmentDO::getCustomerId, q.getCustomerId())
                .le(to != null, ShpShipmentDO::getShipDate, to));
        Map<Long, ShpShipmentDO> sById = all.stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
        Map<Long, LocalDate> firstShip = new HashMap<>();
        for (ShpShipmentLineDO l : shipmentLineMapper.selectByParents(sById.keySet())) {
            LocalDate d = sById.get(l.getShipmentId()).getShipDate();
            if (d == null) continue;
            firstShip.merge(l.getOrderLineId(), d, (a, b) -> a.isBefore(b) ? a : b);
        }
        firstShip.entrySet().removeIf(e -> from != null && e.getValue().isBefore(from));
        Map<Long, SalesOrderLineDTO> ols = orderQueryApi.getLines(firstShip.keySet());
        List<SalesOrderLineDTO> lines = ols.values().stream()
                .filter(l -> q.getOwnerId() == null || q.getOwnerId().equals(l.ownerId()))
                .filter(l -> q.getMaterialId() == null || q.getMaterialId().equals(l.materialId()))
                .sorted(Comparator.comparing(SalesOrderLineDTO::orderNo).thenComparing(SalesOrderLineDTO::lineNo)).toList();
        Map<Long, CustomerDTO> cus = support.customers(lines.stream().map(SalesOrderLineDTO::customerId).toList());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalesOrderLineDTO::materialId).toList());
        Map<Long, UserDTO> users = support.users(lines.stream().map(SalesOrderLineDTO::ownerId).toList());
        Map<String, int[]> byCustomer = new LinkedHashMap<>();
        Map<String, int[]> byOwner = new LinkedHashMap<>();
        Map<String, int[]> byMaterial = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        List<DelayRow> delays = new ArrayList<>();
        int total = 0;
        int onTime = 0;
        for (SalesOrderLineDTO l : lines) {
            LocalDate first = firstShip.get(l.lineId());
            LocalDate due = l.dueDate();
            boolean ok = due == null || !first.isAfter(due);
            total++;
            if (ok) onTime++;
            MaterialDTO m = ms.get(l.materialId());
            String ck = "C" + l.customerId();
            String ok2 = "O" + l.ownerId();
            String mk = "M" + l.materialId();
            labels.put(ck, Objects.toString(ShpSupport.customerName(cus.get(l.customerId())), "-"));
            labels.put(ok2, Objects.toString(ShpSupport.name(users, l.ownerId()), "-"));
            labels.put(mk, m == null ? "-" : m.code() + " " + m.name());
            inc(byCustomer, ck, ok);
            inc(byOwner, ok2, ok);
            inc(byMaterial, mk, ok);
            if (!ok) {
                delays.add(new DelayRow(l.orderId(), l.orderNo(), l.lineNo(), l.customerId(), labels.get(ck), l.materialId(), m == null ? null : m.code(),
                        m == null ? null : m.name(), due, first, ChronoUnit.DAYS.between(due, first), labels.get(ok2)));
            }
        }
        return new OnTimeReport(total, onTime, rate(onTime, total), groups(byCustomer, labels), groups(byOwner, labels), groups(byMaterial, labels), delays);
    }

    private static void inc(Map<String, int[]> map, String key, boolean ok) {
        int[] v = map.computeIfAbsent(key, k -> new int[2]);
        v[0]++;
        if (ok) v[1]++;
    }

    private static List<OnTimeGroup> groups(Map<String, int[]> map, Map<String, String> labels) {
        return map.entrySet().stream().map(e -> new OnTimeGroup(e.getKey().substring(1), labels.get(e.getKey()), e.getValue()[0], e.getValue()[1],
                rate(e.getValue()[1], e.getValue()[0]))).sorted(Comparator.comparing(OnTimeGroup::rate)).toList();
    }

    /** 百分比，保留 1 位小数 */
    static BigDecimal rate(int part, int total) {
        if (total == 0) return null;
        return BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    // ==================== 出口统计 ====================

    /** 按目的国、HS 编码、月份统计出货金额（本位币）与数量 */
    public List<ExportStatRow> exportStats(ReportQuery q) {
        List<ShpShipmentDO> ships = shippedShipments(q);
        Map<Long, ShpShipmentDO> sById = ships.stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
        List<ShpShipmentLineDO> lines = shipmentLineMapper.selectByParents(sById.keySet());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(ShpShipmentLineDO::getMaterialId).toList());
        Map<Long, String> countries = new HashMap<>();
        for (ShpShipmentDO s : ships) {
            AddressDTO a = support.address(s.getShipToSnapshot());
            countries.put(s.getId(), a == null || a.country() == null ? "-" : a.country());
        }
        Map<String, Object[]> acc = new LinkedHashMap<>();
        for (ShpShipmentLineDO l : lines) {
            ShpShipmentDO s = sById.get(l.getShipmentId());
            MaterialDTO m = ms.get(l.getMaterialId());
            String month = s.getShipDate() == null ? "" : s.getShipDate().toString().substring(0, 7);
            String hs = m == null || m.hsCode() == null ? "-" : m.hsCode();
            String key = month + "|" + countries.get(s.getId()) + "|" + hs;
            Object[] v = acc.computeIfAbsent(key, k -> new Object[]{new java.util.HashSet<Long>(), BigDecimal.ZERO, BigDecimal.ZERO});
            @SuppressWarnings("unchecked")
            java.util.Set<Long> set = (java.util.Set<Long>) v[0];
            set.add(s.getId());
            v[1] = ((BigDecimal) v[1]).add(ShpSupport.nz(l.getBaseQty()));
            v[2] = ((BigDecimal) v[2]).add(support.currencyApi().toBase(ShpSupport.nz(l.getTotalAmount()), s.getExchangeRate()));
        }
        boolean price = support.canSeePrice();
        return acc.entrySet().stream().map(e -> {
            String[] k = e.getKey().split("\\|", -1);
            Object[] v = e.getValue();
            return new ExportStatRow(k[0], k[1], k[2], ((java.util.Set<?>) v[0]).size(), (BigDecimal) v[1],
                    price ? ((BigDecimal) v[2]).setScale(2, RoundingMode.HALF_UP) : null);
        }).sorted(Comparator.comparing(ExportStatRow::month).thenComparing(ExportStatRow::country).thenComparing(ExportStatRow::hsCode)).toList();
    }
}
