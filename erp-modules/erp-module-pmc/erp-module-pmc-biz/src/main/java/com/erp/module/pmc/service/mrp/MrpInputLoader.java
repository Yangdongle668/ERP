package com.erp.module.pmc.service.mrp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.engineering.api.bom.BomDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPlanAttr;
import com.erp.module.engineering.api.material.MaterialPurchaseAttr;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.inventory.api.stock.StockSummary;
import com.erp.module.pmc.dal.dataobject.PmcDemandDO;
import com.erp.module.pmc.dal.dataobject.PmcMpsDO;
import com.erp.module.pmc.dal.dataobject.PmcMpsLineDO;
import com.erp.module.pmc.dal.mapper.PmcMpsLineMapper;
import com.erp.module.pmc.dal.mapper.PmcMpsMapper;
import com.erp.module.pmc.service.LeadTimeService;
import com.erp.module.pmc.service.PlanningData;
import com.erp.module.pmc.config.PmcModuleConfig;
import com.erp.module.pmc.service.PmcSupport;
import com.erp.module.pmc.service.Weeks;
import com.erp.module.pmc.service.demand.DemandService;
import com.erp.module.pmc.service.mrp.MrpModel.Comp;
import com.erp.module.pmc.service.mrp.MrpModel.Demand;
import com.erp.module.pmc.service.mrp.MrpModel.Input;
import com.erp.module.pmc.service.mrp.MrpModel.Mat;
import com.erp.module.pmc.service.mrp.MrpModel.Sub;
import com.erp.module.pmc.service.mrp.MrpModel.Supply;
import com.erp.module.production.api.order.OpenOrderDTO;
import com.erp.module.purchase.api.order.InTransitDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MRP 数据准备（需求 06-03 第 3.1 节）：一次性批量读取需求池 / MPS、在制与在制分配、库存、在途，按默认 BOM 展开物料范围。
 */
@Component("pmcMrpInputLoader")
public class MrpInputLoader {

    /** 运算选项 */
    public record Options(String runType, Set<Long> orderLineIds, int horizonDays, boolean includeForecast, boolean includeSafety, boolean useMps,
                          int toleranceDays, boolean useSubstitute) {
    }

    private static final int MAX_MATERIALS = 50_000;

    private final DemandService demandService;
    private final PmcMpsMapper mpsMapper;
    private final PmcMpsLineMapper mpsLineMapper;
    private final PlanningData data;
    private final PmcSupport support;
    private final LeadTimeService leadTime;

    public MrpInputLoader(DemandService demandService, PmcMpsMapper mpsMapper, PmcMpsLineMapper mpsLineMapper, PlanningData data, PmcSupport support,
                          LeadTimeService leadTime) {
        this.leadTime = leadTime;
        this.demandService = demandService;
        this.mpsMapper = mpsMapper;
        this.mpsLineMapper = mpsLineMapper;
        this.data = data;
        this.support = support;
    }

    public Input load(Options o) {
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(o.horizonDays());
        List<Demand> demands = new ArrayList<>();
        // 1. 独立需求：需求池（或 MPS）
        Map<Long, List<PmcMpsLineDO>> mps = o.useMps() ? publishedMps() : Map.of();
        for (PmcDemandDO d : demandService.openDemands(end)) {
            if (!o.includeForecast() && DemandService.FORECAST.equals(d.getDemandType())) continue;
            if ("ORDER".equals(o.runType()) && (!DemandService.SALES_ORDER.equals(d.getDemandType()) || !o.orderLineIds().contains(d.getSourceLineId()))) {
                continue;
            }
            if (mps.containsKey(d.getMaterialId())) continue;
            String no = d.getSourceNo() == null ? null : d.getSourceNo() + (d.getSourceLineNo() == null ? "" : " 行 " + d.getSourceLineNo());
            demands.add(new Demand(d.getMaterialId(), d.getRequiredDate(), d.getOpenQty(), d.getDemandType(), d.getSourceId(), no, null, null));
        }
        if (!"ORDER".equals(o.runType())) {
            mps.forEach((mid, lines) -> {
                for (PmcMpsLineDO l : lines) {
                    LocalDate day = Weeks.monday(l.getWeek());
                    if (l.getPlannedQty().signum() <= 0 || day.isAfter(end) || Weeks.sunday(l.getWeek()).isBefore(today)) continue;
                    demands.add(new Demand(mid, day, l.getPlannedQty(), "MPS", l.getMpsId(), "MPS " + l.getWeek(), null, null));
                }
            });
        }
        // 2. 已有生产订单：产品为在制供应，子件为在制分配
        List<Supply> supplies = new ArrayList<>();
        List<OpenOrderDTO> orders = data.productionQueryApi().getOpenOrders(null);
        Map<Long, BomDTO> bomCache = new HashMap<>();
        for (OpenOrderDTO po : orders) {
            if (po.remainingQty().signum() > 0) {
                supplies.add(new Supply(po.materialId(), "WIP", "MFG_PROD_ORDER", po.id(), po.docNo(), null, po.planEnd(), po.remainingQty()));
            }
            if (!po.materials().isEmpty()) {
                for (OpenOrderDTO.Material m : po.materials()) {
                    if (m.openQty().signum() > 0) {
                        demands.add(new Demand(m.componentId(), po.planStart(), m.openQty(), "ALLOCATION", po.id(), po.docNo(), po.materialId(), null));
                    }
                }
            } else if (po.remainingQty().signum() > 0) {
                BomDTO bom = po.bomId() != null ? bomCache.computeIfAbsent(po.bomId(), k -> data.bomApi().getBom(k).orElse(null))
                        : defaultBom(po.materialId(), bomCache);
                if (bom != null) {
                    for (Comp c : comps(bom, bomCache, 0)) {
                        Demand d = new Demand(c.componentId(), po.planStart(), po.remainingQty().multiply(c.qtyPer()), "ALLOCATION", po.id(), po.docNo(),
                                po.materialId(), null);
                        if (o.useSubstitute()) d.subs = c.subs();
                        demands.add(d);
                    }
                }
            }
        }
        // 3. 物料范围：需求、供应涉及的物料 + 自制 / 委外件的 BOM 子件（逐层）
        Set<Long> seed = new HashSet<>();
        demands.forEach(d -> seed.add(d.materialId));
        supplies.forEach(s -> seed.add(s.materialId));
        if (o.useSubstitute()) {
            demands.forEach(d -> d.subs.forEach(x -> seed.add(x.materialId())));
        }
        Map<Long, Mat> mats = materials(seed, bomCache, o.useSubstitute());
        // 4. 供应：期初可用、在途、待检
        Map<Long, StockSummary> stock = data.stock(mats.keySet());
        for (Long id : mats.keySet()) {
            StockSummary s = stock.get(id);
            BigDecimal avail = s == null ? BigDecimal.ZERO : PmcSupport.max0(s.availableQty());
            supplies.add(new Supply(id, "OPENING", null, null, "期初可用", null, today, avail));
            if (s != null && s.qcQty() != null && s.qcQty().signum() > 0) {
                supplies.add(new Supply(id, "QC", null, null, "待检", null, today.plusDays(1), s.qcQty()));
            }
        }
        String basis = support.params().getString(PmcModuleConfig.P_PO_DATE_BASIS);
        for (Map.Entry<Long, InTransitDTO> e : data.inTransit(mats.keySet()).entrySet()) {
            for (InTransitDTO.Detail d : e.getValue().details()) {
                if (d.qty() == null || d.qty().signum() <= 0) continue;
                LocalDate date = d.dateBy(basis);
                supplies.add(new Supply(e.getKey(), "PURCHASE", d.docType(), d.docId(), d.docNo(), d.lineId(),
                        date == null ? today.plusDays(1) : date, d.qty()));
            }
        }
        return new Input(today, end, o.toleranceDays(), o.includeSafety(), o.useSubstitute(), mats, demands, supplies);
    }

    private Map<Long, List<PmcMpsLineDO>> publishedMps() {
        List<PmcMpsDO> list = mpsMapper.selectList(new LambdaQueryWrapper<PmcMpsDO>().eq(PmcMpsDO::getMpsStatus, "PUBLISHED"));
        Map<Long, List<PmcMpsLineDO>> map = new LinkedHashMap<>();
        for (PmcMpsDO m : list) {
            for (PmcMpsLineDO l : mpsLineMapper.selectByParent(m.getId())) map.computeIfAbsent(l.getMaterialId(), k -> new ArrayList<>()).add(l);
        }
        return map;
    }

    private BomDTO defaultBom(Long materialId, Map<Long, BomDTO> cache) {
        return data.bomApi().getDefaultBom(materialId, LocalDate.now()).orElse(null);
    }

    /** 单层用量（每 1 个父件，含损耗）；虚拟件透过到其子件 */
    private List<Comp> comps(BomDTO bom, Map<Long, BomDTO> cache, int depth) {
        List<Comp> out = new ArrayList<>();
        if (bom == null || depth > 20) return out;
        BigDecimal base = bom.baseQty() == null || bom.baseQty().signum() == 0 ? BigDecimal.ONE : bom.baseQty();
        Map<Long, MaterialDTO> ms = support.materials(bom.lines().stream().map(BomDTO.Line::componentId).toList());
        for (BomDTO.Line l : bom.lines()) {
            BigDecimal per = l.qtyPer().divide(base, 10, RoundingMode.HALF_UP).multiply(BigDecimal.ONE.add(PmcSupport.nz(l.scrapRate())));
            MaterialDTO c = ms.get(l.componentId());
            if (c != null && c.materialType() == MaterialType.PHANTOM) {
                BomDTO sub = defaultBom(c.id(), cache);
                if (sub != null) {
                    for (Comp x : comps(sub, cache, depth + 1)) out.add(new Comp(x.componentId(), x.qtyPer().multiply(per), x.subs()));
                    continue;
                }
            }
            out.add(new Comp(l.componentId(), per, subs(l)));
        }
        return out;
    }

    /** BOM 行的替代料，按优先级；比例无效的忽略 */
    private static List<Sub> subs(BomDTO.Line l) {
        if (l.substitutes() == null || l.substitutes().isEmpty()) return List.of();
        return l.substitutes().stream()
                .filter(x -> x.substituteId() != null && x.ratio() != null && x.ratio().signum() > 0)
                .sorted(Comparator.comparingInt(BomDTO.Substitute::priority))
                .map(x -> new Sub(x.substituteId(), x.ratio())).toList();
    }

    private Map<Long, Mat> materials(Collection<Long> seed, Map<Long, BomDTO> cache, boolean withSubs) {
        Map<Long, Mat> mats = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>(seed);
        Set<Long> seen = new HashSet<>(seed);
        while (!queue.isEmpty() && mats.size() < MAX_MATERIALS) {
            List<Long> batch = new ArrayList<>();
            while (!queue.isEmpty() && batch.size() < 200) batch.add(queue.poll());
            Map<Long, MaterialDTO> ms = support.materials(batch);
            for (Long id : batch) {
                MaterialDTO m = ms.get(id);
                if (m == null) continue;
                MaterialPlanAttr pa = support.materialApi().getPlanAttr(id);
                String source = pa != null && pa.sourceType() != null ? pa.sourceType().name()
                        : m.sourceType() != null ? m.sourceType().name()
                        : m.materialType() == MaterialType.FINISHED || m.materialType() == MaterialType.SEMI_FINISHED ? "MAKE" : "PURCHASE";
                List<Comp> comps = List.of();
                Long bomId = null;
                if (!"PURCHASE".equals(source)) {
                    BomDTO bom = defaultBom(id, cache);
                    if (bom != null) {
                        bomId = bom.id();
                        comps = comps(bom, cache, 0);
                        for (Comp c : comps) {
                            if (seen.add(c.componentId())) queue.add(c.componentId());
                            if (withSubs) for (Sub x : c.subs()) if (seen.add(x.materialId())) queue.add(x.materialId());
                        }
                    }
                }
                MaterialPurchaseAttr pur = "PURCHASE".equals(source) || "OUTSOURCE".equals(source) ? support.materialApi().getPurchaseAttr(id) : null;
                int lead = pa != null ? pa.leadTimeDays() : 0;
                if (lead <= 0 && pur != null) lead = pur.leadTimeDays();
                BigDecimal moq = pa != null && pa.moq() != null && pa.moq().signum() > 0 ? pa.moq() : pur == null ? null : pur.moq();
                BigDecimal mpq = pa != null && pa.mpq() != null && pa.mpq().signum() > 0 ? pa.mpq() : pur == null ? null : pur.mpq();
                mats.put(id, new Mat(id, m.code(), m.baseUom(), support.precision(m.baseUom()), source, m.status() != MaterialStatus.DISABLED, lead,
                        pa == null ? null : pa.safetyStock(), pa == null || pa.orderPolicy() == null ? "LOT_FOR_LOT" : pa.orderPolicy().name(),
                        pa == null ? null : pa.fixedLotQty(), pa == null ? null : pa.periodDays(), moq, mpq, pa == null ? null : pa.plannerId(),
                        pur == null ? null : pur.buyerId(), bomId, comps, null));
            }
        }
        // 按工艺工时换算的生产提前期（参数开启时，只对自制件）
        Map<Long, LeadTimeService.RoutingTime> times = leadTime.routingTimes(mats.values().stream().filter(x -> "MAKE".equals(x.sourceType()))
                .map(Mat::id).toList());
        times.forEach((id, t) -> {
            Mat x = mats.get(id);
            mats.put(id, new Mat(x.id(), x.code(), x.uom(), x.scale(), x.sourceType(), x.enabled(), x.leadTimeDays(), x.safetyStock(), x.orderPolicy(),
                    x.fixedLotQty(), x.periodDays(), x.moq(), x.mpq(), x.plannerId(), x.buyerId(), x.bomId(), x.comps(), t));
        });
        return mats;
    }
}
