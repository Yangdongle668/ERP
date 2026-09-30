package com.erp.module.pmc.service.shortage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.module.engineering.api.bom.BomDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPurchaseAttr;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.pmc.api.PmcErrorCodes;
import com.erp.module.pmc.api.query.ShortageDTO;
import com.erp.module.pmc.controller.vo.CommonVOs.BatchResult;
import com.erp.module.pmc.controller.vo.ShortageVOs.AnalyzeReq;
import com.erp.module.pmc.controller.vo.ShortageVOs.AnalyzeResult;
import com.erp.module.pmc.controller.vo.ShortageVOs.LineRow;
import com.erp.module.pmc.controller.vo.ShortageVOs.MaterialRow;
import com.erp.module.pmc.controller.vo.ShortageVOs.OrderRow;
import com.erp.module.pmc.controller.vo.ShortageVOs.SnapshotRow;
import com.erp.module.pmc.controller.vo.ShortageVOs.SupplyItem;
import com.erp.module.pmc.dal.dataobject.PmcPushLogDO;
import com.erp.module.pmc.dal.dataobject.PmcShortageOrderDO;
import com.erp.module.pmc.dal.dataobject.PmcShortageSnapshotDO;
import com.erp.module.pmc.dal.mapper.PmcPushLogMapper;
import com.erp.module.pmc.dal.mapper.PmcShortageOrderMapper;
import com.erp.module.pmc.dal.mapper.PmcShortageSnapshotMapper;
import com.erp.module.pmc.service.PlanningData;
import com.erp.module.pmc.service.PlanningData.Supply;
import com.erp.module.pmc.service.PmcSupport;
import com.erp.module.production.api.order.OpenOrderDTO;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.user.UserDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 缺料分析（需求 06-06）：生产订单按 优先级 → 计划开工 → 单号 依次从剩余可用库存分配未领数量，缺料再依次匹配在途（采购、待检、委外），
 * 得到预计齐套日期；分析只读取数据，不占用库存。
 */
@Service("pmcShortageService")
public class ShortageService {

    private static final List<String> DEFAULT_STATUSES = List.of("PLANNED", "RELEASED", "IN_PROGRESS");

    private final PmcShortageOrderMapper orderMapper;
    private final PmcShortageSnapshotMapper lineMapper;
    private final PmcPushLogMapper pushLogMapper;
    private final PlanningData data;
    private final PmcSupport support;
    private final SalesOrderQueryApi salesOrderQueryApi;
    private final ObjectMapper objectMapper;

    public ShortageService(PmcShortageOrderMapper orderMapper, PmcShortageSnapshotMapper lineMapper, PmcPushLogMapper pushLogMapper, PlanningData data,
                           PmcSupport support, SalesOrderQueryApi salesOrderQueryApi, ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
        this.pushLogMapper = pushLogMapper;
        this.data = data;
        this.support = support;
        this.salesOrderQueryApi = salesOrderQueryApi;
        this.objectMapper = objectMapper;
    }

    // ==================== 计算 ====================

    /** 一张订单的一行用料 */
    public static final class Line {
        public Long componentId;
        public BigDecimal perUnit;
        public BigDecimal issuedNet;
        public BigDecimal unissued;
        public BigDecimal allocated = BigDecimal.ZERO;
        public BigDecimal shortage = BigDecimal.ZERO;
        public BigDecimal noSupply = BigDecimal.ZERO;
        public LocalDate eta;
        public final List<SupplyItem> supplies = new ArrayList<>();
    }

    /** 一张订单的分析结果 */
    public static final class OrderResult {
        public OpenOrderDTO order;
        public final List<Line> lines = new ArrayList<>();
        public BigDecimal kitableQty = BigDecimal.ZERO;
        public LocalDate eta;
        public boolean noSupply;

        public boolean short_() {
            return lines.stream().anyMatch(l -> l.shortage.signum() > 0);
        }
    }

    /** 按顺序分配（调用方负责排序）；返回每张订单的结果 */
    public List<OrderResult> compute(List<OpenOrderDTO> orders) {
        Map<Long, BomDTO> bomCache = new HashMap<>();
        List<OrderResult> results = new ArrayList<>();
        Set<Long> comps = new HashSet<>();
        for (OpenOrderDTO o : orders) {
            OrderResult r = new OrderResult();
            r.order = o;
            if (!o.materials().isEmpty()) {
                for (OpenOrderDTO.Material m : o.materials()) {
                    Line l = new Line();
                    l.componentId = m.componentId();
                    l.perUnit = PmcSupport.nz(m.qtyPer()).multiply(BigDecimal.ONE.add(PmcSupport.nz(m.scrapRate())));
                    l.issuedNet = PmcSupport.max0(PmcSupport.nz(m.issuedQty()).subtract(PmcSupport.nz(m.returnedGoodQty())));
                    l.unissued = PmcSupport.max0(m.openQty());
                    r.lines.add(l);
                }
            } else {
                BomDTO bom = o.bomId() != null ? bomCache.computeIfAbsent(o.bomId(), k -> data.bomApi().getBom(k).orElse(null))
                        : data.bomApi().getDefaultBom(o.materialId(), LocalDate.now()).orElse(null);
                Map<Long, BigDecimal> per = new LinkedHashMap<>();
                perUnit(bom, BigDecimal.ONE, per, 0);
                per.forEach((c, q) -> {
                    Line l = new Line();
                    l.componentId = c;
                    l.perUnit = q;
                    l.issuedNet = BigDecimal.ZERO;
                    l.unissued = o.remainingQty().multiply(q);
                    r.lines.add(l);
                });
            }
            r.lines.forEach(l -> comps.add(l.componentId));
            results.add(r);
        }
        Map<Long, BigDecimal> avail = new HashMap<>(data.available(comps));
        Map<Long, List<Supply>> sup = data.supplies(comps);
        Map<Long, List<BigDecimal>> supLeft = new HashMap<>();
        sup.forEach((k, v) -> supLeft.put(k, new ArrayList<>(v.stream().map(Supply::qty).toList())));
        for (OrderResult r : results) {
            BigDecimal kit = null;
            for (Line l : r.lines) {
                BigDecimal have = avail.getOrDefault(l.componentId, BigDecimal.ZERO);
                l.allocated = PmcSupport.min(have, l.unissued);
                avail.put(l.componentId, have.subtract(l.allocated));
                l.shortage = l.unissued.subtract(l.allocated);
                BigDecimal gap = l.shortage;
                List<Supply> ss = sup.getOrDefault(l.componentId, List.of());
                List<BigDecimal> left = supLeft.getOrDefault(l.componentId, List.of());
                for (int i = 0; i < ss.size() && gap.signum() > 0; i++) {
                    BigDecimal rem = left.get(i);
                    if (rem.signum() <= 0) continue;
                    BigDecimal take = PmcSupport.min(rem, gap);
                    left.set(i, rem.subtract(take));
                    gap = gap.subtract(take);
                    Supply s = ss.get(i);
                    l.supplies.add(new SupplyItem(s.docType(), s.docNo(), s.date(), take));
                    l.eta = s.date();
                }
                l.noSupply = gap;
                if (l.shortage.signum() > 0) {
                    if (gap.signum() > 0) r.noSupply = true;
                    else if (l.eta != null && (r.eta == null || l.eta.isAfter(r.eta))) r.eta = l.eta;
                }
                if (l.perUnit.signum() > 0) {
                    BigDecimal k = l.issuedNet.add(l.allocated).divide(l.perUnit, 4, RoundingMode.DOWN);
                    kit = kit == null ? k : kit.min(k);
                }
            }
            BigDecimal remaining = r.order.remainingQty();
            r.kitableQty = kit == null ? remaining : PmcSupport.min(remaining, kit.setScale(0, RoundingMode.DOWN));
            if (r.noSupply) r.eta = null;
        }
        return results;
    }

    private void perUnit(BomDTO bom, BigDecimal mult, Map<Long, BigDecimal> out, int depth) {
        if (bom == null || depth > 20) return;
        BigDecimal base = bom.baseQty() == null || bom.baseQty().signum() == 0 ? BigDecimal.ONE : bom.baseQty();
        Map<Long, MaterialDTO> ms = support.materials(bom.lines().stream().map(BomDTO.Line::componentId).toList());
        for (BomDTO.Line l : bom.lines()) {
            BigDecimal q = mult.multiply(l.qtyPer()).divide(base, 10, RoundingMode.HALF_UP).multiply(BigDecimal.ONE.add(PmcSupport.nz(l.scrapRate())));
            MaterialDTO c = ms.get(l.componentId());
            if (c != null && c.materialType() == MaterialType.PHANTOM) {
                BomDTO sub = data.bomApi().getDefaultBom(c.id(), LocalDate.now()).orElse(null);
                if (sub != null) {
                    perUnit(sub, q, out, depth + 1);
                    continue;
                }
            }
            out.merge(l.componentId(), q, BigDecimal::add);
        }
    }

    /** 全部未完工订单（不含暂停）按分配顺序 */
    public List<OpenOrderDTO> ordered(List<OpenOrderDTO> orders) {
        List<OpenOrderDTO> list = new ArrayList<>(orders);
        list.sort(Comparator.comparingInt(OpenOrderDTO::priority).thenComparing(OpenOrderDTO::planStart).thenComparing(OpenOrderDTO::docNo));
        return list;
    }

    /** 实时计算单张订单的缺料（可用库存先分配给排在它前面的订单） */
    public List<ShortageDTO> shortageOf(Long prodOrderId) {
        List<OpenOrderDTO> all = ordered(data.productionQueryApi().getOpenOrders(null).stream()
                .filter(o -> !"SUSPENDED".equals(o.prodStatus())).toList());
        List<OpenOrderDTO> upTo = new ArrayList<>();
        for (OpenOrderDTO o : all) {
            upTo.add(o);
            if (o.id().equals(prodOrderId)) break;
        }
        if (upTo.isEmpty() || !upTo.get(upTo.size() - 1).id().equals(prodOrderId)) return List.of();
        OrderResult r = compute(upTo).get(upTo.size() - 1);
        return r.lines.stream().filter(l -> l.shortage.signum() > 0)
                .map(l -> new ShortageDTO(prodOrderId, l.componentId, l.unissued, l.allocated, l.shortage, l.noSupply.signum() > 0 ? null : l.eta, l.noSupply))
                .toList();
    }

    // ==================== 分析与快照 ====================

    @Transactional(rollbackFor = Exception.class)
    public AnalyzeResult analyze(AnalyzeReq req) {
        List<String> statuses = req == null || req.statuses() == null || req.statuses().isEmpty() ? DEFAULT_STATUSES : req.statuses();
        Set<Long> depts = req == null || req.deptId() == null ? null : support.deptAndChildren(req.deptId());
        Set<Long> ids = req == null || req.prodOrderIds() == null ? Set.of() : new HashSet<>(req.prodOrderIds());
        List<OpenOrderDTO> orders = ordered(data.productionQueryApi().getOpenOrders(null).stream()
                .filter(o -> statuses.contains(o.prodStatus()))
                .filter(o -> !ids.isEmpty() ? ids.contains(o.id()) : (depts == null || depts.contains(o.deptId()))
                        && (req == null || req.planStartFrom() == null || !o.planStart().isBefore(req.planStartFrom()))
                        && (req == null || req.planStartTo() == null || !o.planStart().isAfter(req.planStartTo())))
                .toList());
        if (orders.isEmpty()) return new AnalyzeResult(null, 0, 0, 0);
        List<OrderResult> results = compute(orders);
        String no = "SA" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ThreadLocalRandom.current().nextInt(100, 1000);
        Map<Long, SalesOrderLineDTO> sales = salesOrderQueryApi.getLines(orders.stream().map(OpenOrderDTO::salesOrderLineId).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Set<Long> comps = new HashSet<>();
        results.forEach(r -> r.lines.forEach(l -> comps.add(l.componentId)));
        Map<Long, MaterialPurchaseAttr> pur = support.purchaseAttrs(comps);
        int sort = 0;
        int shortOrders = 0;
        Set<Long> shortMaterials = new HashSet<>();
        for (OrderResult r : results) {
            OpenOrderDTO o = r.order;
            PmcShortageOrderDO so = new PmcShortageOrderDO();
            so.setSnapshotNo(no);
            so.setProdOrderId(o.id());
            so.setProdOrderNo(o.docNo());
            so.setProductId(o.materialId());
            so.setQty(o.qty());
            so.setPlanStart(o.planStart());
            so.setPriority(o.priority());
            so.setProdStatus(o.prodStatus());
            so.setDeptId(o.deptId());
            so.setSalesOrderNo(o.salesOrderNo());
            SalesOrderLineDTO sl = o.salesOrderLineId() == null ? null : sales.get(o.salesOrderLineId());
            so.setCustomerDate(sl == null ? null : sl.dueDate());
            int lines = r.lines.size();
            int shortLines = (int) r.lines.stream().filter(l -> l.shortage.signum() > 0).count();
            BigDecimal un = PmcSupport.sum(r.lines.stream().map(l -> l.unissued).toList());
            BigDecimal al = PmcSupport.sum(r.lines.stream().map(l -> l.allocated).toList());
            so.setLineCount(lines);
            so.setShortLineCount(shortLines);
            so.setLineKitRate(lines == 0 ? BigDecimal.ONE : BigDecimal.valueOf(lines - shortLines).divide(BigDecimal.valueOf(lines), 4, RoundingMode.HALF_UP));
            so.setQtyKitRate(un.signum() == 0 ? BigDecimal.ONE : al.divide(un, 4, RoundingMode.HALF_UP));
            so.setKitableQty(r.kitableQty);
            so.setEtaDate(r.eta);
            so.setHasNoSupply(r.noSupply);
            so.setSortNo(++sort);
            orderMapper.insert(so);
            if (shortLines > 0) shortOrders++;
            for (Line l : r.lines) {
                PmcShortageSnapshotDO d = new PmcShortageSnapshotDO();
                d.setSnapshotNo(no);
                d.setProdOrderId(o.id());
                d.setComponentId(l.componentId);
                d.setNeedDate(o.planStart());
                d.setUnissuedQty(l.unissued);
                d.setAllocatedQty(l.allocated);
                d.setShortageQty(l.shortage);
                d.setSupplyDetail(json(l.supplies));
                d.setEtaDate(l.noSupply.signum() > 0 ? null : l.eta);
                d.setNoSupplyQty(l.noSupply);
                MaterialPurchaseAttr a = pur.get(l.componentId);
                d.setBuyerId(a == null ? null : a.buyerId());
                lineMapper.insert(d);
                if (l.shortage.signum() > 0) shortMaterials.add(l.componentId);
            }
        }
        return new AnalyzeResult(no, results.size(), shortOrders, shortMaterials.size());
    }

    private String json(List<SupplyItem> items) {
        if (items.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<SupplyItem> supplies(String json) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String latestNo() {
        PmcShortageOrderDO o = orderMapper.selectOne(new LambdaQueryWrapper<PmcShortageOrderDO>().orderByDesc(PmcShortageOrderDO::getCreatedAt)
                .orderByDesc(PmcShortageOrderDO::getId).last("LIMIT 1"));
        return o == null ? null : o.getSnapshotNo();
    }

    private String snapshot(String no) {
        String s = no == null || no.isBlank() ? latestNo() : no.trim();
        if (s == null) return null;
        if (orderMapper.selectCount(new LambdaQueryWrapper<PmcShortageOrderDO>().eq(PmcShortageOrderDO::getSnapshotNo, s)) == 0) {
            throw new BizException(PmcErrorCodes.SHORTAGE_SNAPSHOT_NOT_EXISTS);
        }
        return s;
    }

    public List<OrderRow> orders(String snapshotNo) {
        String no = snapshot(snapshotNo);
        if (no == null) return List.of();
        List<PmcShortageOrderDO> list = orderMapper.selectList(new LambdaQueryWrapper<PmcShortageOrderDO>().eq(PmcShortageOrderDO::getSnapshotNo, no)
                .orderByAsc(PmcShortageOrderDO::getSortNo));
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(PmcShortageOrderDO::getProductId).toList());
        return list.stream().map(o -> {
            MaterialDTO m = ms.get(o.getProductId());
            return new OrderRow(o.getProdOrderId(), o.getProdOrderNo(), o.getPriority(), o.getProductId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), o.getQty(), o.getPlanStart(), o.getProdStatus(), o.getLineKitRate(), o.getQtyKitRate(), o.getKitableQty(),
                    o.getLineCount(), o.getShortLineCount(), o.getEtaDate(),
                    o.getEtaDate() != null && o.getPlanStart() != null && o.getEtaDate().isAfter(o.getPlanStart()), Boolean.TRUE.equals(o.getHasNoSupply()),
                    o.getSalesOrderNo(), o.getCustomerDate());
        }).toList();
    }

    public List<LineRow> lines(String snapshotNo, Long prodOrderId, boolean shortOnly) {
        String no = snapshot(snapshotNo);
        if (no == null) return List.of();
        List<PmcShortageSnapshotDO> list = lineMapper.selectList(new LambdaQueryWrapper<PmcShortageSnapshotDO>().eq(PmcShortageSnapshotDO::getSnapshotNo, no)
                .eq(prodOrderId != null, PmcShortageSnapshotDO::getProdOrderId, prodOrderId).gt(shortOnly, PmcShortageSnapshotDO::getShortageQty, 0)
                .orderByAsc(PmcShortageSnapshotDO::getId));
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(PmcShortageSnapshotDO::getComponentId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(PmcShortageSnapshotDO::getBuyerId).toList());
        return list.stream().map(l -> {
            MaterialDTO m = ms.get(l.getComponentId());
            return new LineRow(l.getProdOrderId(), l.getComponentId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.baseUom(), l.getUnissuedQty(), l.getAllocatedQty(), l.getShortageQty(), supplies(l.getSupplyDetail()),
                    l.getEtaDate(), l.getNoSupplyQty(), l.getBuyerId(), PmcSupport.name(users, l.getBuyerId()));
        }).toList();
    }

    /** 物料视图：按物料汇总缺料（采购统一催料） */
    public List<MaterialRow> materials(String snapshotNo) {
        String no = snapshot(snapshotNo);
        if (no == null) return List.of();
        List<PmcShortageSnapshotDO> list = lineMapper.selectList(new LambdaQueryWrapper<PmcShortageSnapshotDO>().eq(PmcShortageSnapshotDO::getSnapshotNo, no)
                .gt(PmcShortageSnapshotDO::getShortageQty, 0).orderByAsc(PmcShortageSnapshotDO::getId));
        Map<Long, String> orderNos = orderMapper.selectList(new LambdaQueryWrapper<PmcShortageOrderDO>().eq(PmcShortageOrderDO::getSnapshotNo, no))
                .stream().collect(Collectors.toMap(PmcShortageOrderDO::getProdOrderId, PmcShortageOrderDO::getProdOrderNo, (a, b) -> a));
        Map<Long, List<PmcShortageSnapshotDO>> by = list.stream().collect(Collectors.groupingBy(PmcShortageSnapshotDO::getComponentId, LinkedHashMap::new,
                Collectors.toList()));
        Map<Long, MaterialDTO> ms = support.materials(by.keySet());
        Map<Long, UserDTO> users = support.users(list.stream().map(PmcShortageSnapshotDO::getBuyerId).toList());
        List<MaterialRow> out = new ArrayList<>();
        by.forEach((cid, ls) -> {
            MaterialDTO m = ms.get(cid);
            List<SupplyItem> sups = new ArrayList<>();
            ls.forEach(l -> sups.addAll(supplies(l.getSupplyDetail())));
            LocalDate first = ls.stream().map(PmcShortageSnapshotDO::getNeedDate).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            Long buyer = ls.get(0).getBuyerId();
            out.add(new MaterialRow(cid, m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.baseUom(),
                    PmcSupport.sum(ls.stream().map(PmcShortageSnapshotDO::getShortageQty).toList()), ls.size(), first, sups,
                    PmcSupport.sum(ls.stream().map(PmcShortageSnapshotDO::getNoSupplyQty).toList()), buyer, PmcSupport.name(users, buyer),
                    ls.stream().map(l -> orderNos.get(l.getProdOrderId())).distinct().toList()));
        });
        return out;
    }

    public List<SnapshotRow> snapshots() {
        List<PmcShortageOrderDO> list = orderMapper.selectList(new LambdaQueryWrapper<PmcShortageOrderDO>()
                .ge(PmcShortageOrderDO::getCreatedAt, LocalDateTime.now().minusDays(30)).orderByDesc(PmcShortageOrderDO::getCreatedAt).orderByDesc(PmcShortageOrderDO::getId));
        Map<String, List<PmcShortageOrderDO>> by = list.stream().collect(Collectors.groupingBy(PmcShortageOrderDO::getSnapshotNo, LinkedHashMap::new,
                Collectors.toList()));
        Map<Long, UserDTO> users = support.users(list.stream().map(PmcShortageOrderDO::getCreatedBy).toList());
        List<SnapshotRow> out = new ArrayList<>();
        by.forEach((no, ls) -> out.add(new SnapshotRow(no, ls.get(0).getCreatedAt(), ls.size(),
                (int) ls.stream().filter(o -> o.getShortLineCount() > 0).count(), PmcSupport.name(users, ls.get(0).getCreatedBy()))));
        return out.size() > 50 ? out.subList(0, 50) : out;
    }

    /** 推送催料（PMC-SHT-R03：同一物料一天只推送一次），给采购员发送待办 */
    @Transactional(rollbackFor = Exception.class)
    public BatchResult push(String snapshotNo, List<Long> componentIds) {
        String no = snapshot(snapshotNo);
        List<String> errors = new ArrayList<>();
        int n = 0;
        if (no == null) return new BatchResult(0, List.of("请先进行缺料分析"), List.of());
        LocalDate today = LocalDate.now();
        for (MaterialRow m : materials(no)) {
            if (componentIds != null && !componentIds.isEmpty() && !componentIds.contains(m.componentId())) continue;
            if (m.buyerId() == null) {
                errors.add(m.componentCode() + "：没有设置采购员");
                continue;
            }
            boolean pushed = pushLogMapper.selectCount(new LambdaQueryWrapper<PmcPushLogDO>().eq(PmcPushLogDO::getPushType, "SHORTAGE")
                    .eq(PmcPushLogDO::getRefKey, String.valueOf(m.componentId())).eq(PmcPushLogDO::getPushDate, today)) > 0;
            if (pushed) {
                errors.add(m.componentCode() + "：今天已推送过");
                continue;
            }
            String title = "催料：" + m.componentCode() + " " + m.componentName() + " 缺 " + PmcSupport.plain(m.shortageQty())
                    + (m.firstNeedDate() == null ? "" : "，最早需要 " + m.firstNeedDate()) + "，影响 " + String.join("、", m.orderNos());
            support.todo("PMC_SHORTAGE:" + m.componentId() + ":" + today, List.of(m.buyerId()), "PMC_SHORTAGE", m.componentId(), m.componentCode(), title,
                    "/pmc/shortage?snapshotNo=" + no);
            PmcPushLogDO log = new PmcPushLogDO();
            log.setPushType("SHORTAGE");
            log.setRefKey(String.valueOf(m.componentId()));
            log.setPushDate(today);
            log.setUserIds(String.valueOf(m.buyerId()));
            pushLogMapper.insert(log);
            n++;
        }
        return new BatchResult(n, errors, List.of());
    }

    /** 快照保留 30 天（PMC-SHT-R04） */
    @ErpJob(code = "PMC_SHORTAGE_CLEANUP", name = "缺料快照清理", cron = "0 20 3 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public String cleanup() {
        LocalDateTime before = LocalDateTime.now().minusDays(30);
        int a = orderMapper.delete(new LambdaQueryWrapper<PmcShortageOrderDO>().lt(PmcShortageOrderDO::getCreatedAt, before));
        int b = lineMapper.delete(new LambdaQueryWrapper<PmcShortageSnapshotDO>().lt(PmcShortageSnapshotDO::getCreatedAt, before));
        return "删除快照 " + a + " 张订单、" + b + " 行";
    }
}
