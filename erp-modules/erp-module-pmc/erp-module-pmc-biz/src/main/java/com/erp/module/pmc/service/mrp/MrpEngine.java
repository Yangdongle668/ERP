package com.erp.module.pmc.service.mrp;

import com.erp.common.exception.BizException;
import com.erp.module.pmc.api.PmcErrorCodes;
import com.erp.module.pmc.service.mrp.MrpModel.Balance;
import com.erp.module.pmc.service.mrp.MrpModel.Comp;
import com.erp.module.pmc.service.mrp.MrpModel.Demand;
import com.erp.module.pmc.service.mrp.MrpModel.Input;
import com.erp.module.pmc.service.mrp.MrpModel.Mat;
import com.erp.module.pmc.service.mrp.MrpModel.Output;
import com.erp.module.pmc.service.mrp.MrpModel.Peg;
import com.erp.module.pmc.service.mrp.MrpModel.Planned;
import com.erp.module.pmc.service.mrp.MrpModel.Supply;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MRP 计算（需求 06-03 第 3.2 节），纯内存：按低位码逐层，逐笔需求消耗已有供应（先期初、再按日期；未来供应视为提前并产生例外），
 * 仍不足时按批量规则生成计划订单，自制 / 委外的计划订单按 BOM 展开为子件的相关需求（日期 = 父件下达日期）。
 */
public final class MrpEngine {

    private static final Map<String, Integer> TYPE_ORDER = Map.of("SAFETY_STOCK", 0, "ALLOCATION", 1, "SALES_ORDER", 2, "MPS", 3, "MANUAL", 4,
            "PARENT", 5, "FORECAST", 6);

    /** 可消耗的一笔供应：已有供应或计划订单 */
    private static final class Lot {
        final LocalDate date;
        final Supply supply;
        final Planned planned;

        Lot(LocalDate date, Supply supply, Planned planned) {
            this.date = date;
            this.supply = supply;
            this.planned = planned;
        }

        BigDecimal remaining() {
            return supply != null ? supply.remaining : planned.remaining;
        }

        void take(BigDecimal q) {
            if (supply != null) supply.remaining = supply.remaining.subtract(q);
            else planned.remaining = planned.remaining.subtract(q);
        }

        int rank() {
            return supply != null && "OPENING".equals(supply.type) ? 0 : 1;
        }
    }

    private final Input in;
    private final Map<Long, List<Demand>> demands = new HashMap<>();
    private final Map<Long, List<Supply>> supplies = new HashMap<>();
    private final List<Planned> planned = new ArrayList<>();
    private final List<MrpModel.Exception> exceptions = new ArrayList<>();
    private final List<Balance> balances = new ArrayList<>();
    private final Set<Long> done = new HashSet<>();
    private int seq;

    private MrpEngine(Input in) {
        this.in = in;
    }

    public static Output run(Input in) {
        return new MrpEngine(in).compute();
    }

    private Output compute() {
        for (Demand d : in.demands()) demands.computeIfAbsent(d.materialId, k -> new ArrayList<>()).add(d);
        for (Supply s : in.supplies()) supplies.computeIfAbsent(s.materialId, k -> new ArrayList<>()).add(s);
        Map<Long, Integer> llc = lowLevelCodes();
        List<Mat> order = new ArrayList<>(in.mats().values());
        order.sort(Comparator.comparing((Mat m) -> llc.getOrDefault(m.id(), 0)).thenComparing(Mat::code, Comparator.nullsLast(Comparator.naturalOrder())));
        int count = 0;
        for (Mat m : order) {
            if (plan(m)) count++;
            done.add(m.id());
        }
        return new Output(planned, exceptions, balances, count);
    }

    /** 低位码：父件 → 子件的最长路径；发现循环时运算失败并指出物料 */
    private Map<Long, Integer> lowLevelCodes() {
        Map<Long, Integer> state = new HashMap<>();
        List<Long> post = new ArrayList<>();
        for (Long id : in.mats().keySet()) visit(id, state, post);
        Map<Long, Integer> llc = new HashMap<>();
        for (int i = post.size() - 1; i >= 0; i--) {
            Long id = post.get(i);
            Mat m = in.mats().get(id);
            int level = llc.getOrDefault(id, 0);
            llc.putIfAbsent(id, 0);
            if (m == null || !m.make()) continue;
            for (Comp c : m.comps()) llc.merge(c.componentId(), level + 1, Math::max);
        }
        return llc;
    }

    private void visit(Long id, Map<Long, Integer> state, List<Long> post) {
        Integer st = state.get(id);
        if (st != null) {
            if (st == 1) throw BizException.of(PmcErrorCodes.MRP_BOM_LOOP, code(id));
            return;
        }
        state.put(id, 1);
        Mat m = in.mats().get(id);
        if (m != null && m.make()) for (Comp c : m.comps()) visit(c.componentId(), state, post);
        state.put(id, 2);
        post.add(id);
    }

    private String code(Long id) {
        Mat m = in.mats().get(id);
        return m == null ? String.valueOf(id) : m.code();
    }

    /** 计算一个物料，返回是否有需求或供应参与计算 */
    private boolean plan(Mat m) {
        List<Demand> ds = new ArrayList<>(demands.getOrDefault(m.id(), List.of()));
        List<Supply> ss = supplies.getOrDefault(m.id(), List.of()).stream().filter(s -> !s.date.isAfter(in.horizonEnd())).toList();
        if (!m.enabled()) {
            BigDecimal q = sum(ds);
            if (q.signum() > 0) {
                exceptions.add(new MrpModel.Exception(m.id(), "DISABLED", null, null, null, null, null, null, q,
                        "物料「" + m.code() + "」已停用，需求 " + plain(q) + " 未生成建议"));
            }
            return !ds.isEmpty();
        }
        if (in.includeSafety() && m.safetyStock() != null && m.safetyStock().signum() > 0) {
            ds.add(new Demand(m.id(), in.today(), m.safetyStock(), "SAFETY_STOCK", null, "安全库存", null, null));
        }
        if (ds.isEmpty() && ss.stream().allMatch(s -> "OPENING".equals(s.type))) return false;
        ds.sort(Comparator.comparing((Demand d) -> eff(d.date)).thenComparing(d -> TYPE_ORDER.getOrDefault(d.type, 9)));
        List<Lot> lots = new ArrayList<>();
        for (Supply s : ss) lots.add(new Lot(eff(s.date), s, null));
        lots.sort(Comparator.comparing(Lot::rank).thenComparing(l -> l.date));
        for (int i = 0; i < ds.size(); i++) {
            Demand d = ds.get(i);
            LocalDate day = eff(d.date);
            BigDecimal need = d.qty;
            for (Lot l : lots) {
                if (need.signum() <= 0) break;
                BigDecimal r = l.remaining();
                if (r.signum() <= 0) continue;
                BigDecimal take = r.min(need);
                l.take(take);
                need = need.subtract(take);
                if (l.supply != null && l.supply.firstUse == null) l.supply.firstUse = day;
                if (l.planned != null) peg(l.planned, d, take, day);
            }
            if (need.signum() > 0 && in.useSubstitute() && !d.subs.isEmpty()) need = substitute(m, d, need, day);
            if (need.signum() > 0) {
                Planned p = newPlanned(m, need, day, ds, i, lots);
                peg(p, d, need, day);
                p.remaining = p.qty.subtract(need);
                int pos = 0;
                while (pos < lots.size() && (lots.get(pos).rank() == 0 || !lots.get(pos).date.isAfter(day))) pos++;
                lots.add(pos, new Lot(day, null, p));
            }
        }
        for (Supply s : ss) {
            balances.add(new Balance(m.id(), s.date, s.type, s.docNo, null, BigDecimal.ZERO, s.qty));
            exceptionOf(m, s);
        }
        for (Demand d : ds) balances.add(new Balance(m.id(), d.date, d.type, d.sourceNo, d.parentMaterialId, d.qty, BigDecimal.ZERO));
        return true;
    }

    private LocalDate eff(LocalDate d) {
        return d.isBefore(in.today()) ? in.today() : d;
    }

    private static void peg(Planned p, Demand d, BigDecimal qty, LocalDate day) {
        p.pegs.add(new Peg(d.type, d.sourceId, d.sourceNo, d.parentMaterialId, d.parent, qty, day));
    }

    /** 按批量规则生成计划订单，并把自制 / 委外订单展开为子件需求 */
    private Planned newPlanned(Mat m, BigDecimal net, LocalDate day, List<Demand> ds, int index, List<Lot> lots) {
        BigDecimal q = net;
        if ("PERIOD".equals(m.orderPolicy()) && m.periodDays() != null && m.periodDays() > 1) {
            LocalDate end = day.plusDays(m.periodDays());
            BigDecimal later = BigDecimal.ZERO;
            for (int j = index + 1; j < ds.size(); j++) {
                if (!eff(ds.get(j).date).isBefore(end)) break;
                later = later.add(ds.get(j).qty);
            }
            BigDecimal future = BigDecimal.ZERO;
            for (Lot l : lots) if (l.date.isBefore(end) && l.date.isAfter(day)) future = future.add(l.remaining());
            q = q.add(max0(later.subtract(future)));
        } else if ("FIXED_QTY".equals(m.orderPolicy()) && pos(m.fixedLotQty())) {
            q = ceilTo(q, m.fixedLotQty());
        }
        if (pos(m.moq()) && q.compareTo(m.moq()) < 0) q = m.moq();
        if (pos(m.mpq())) q = ceilTo(q, m.mpq());
        q = q.setScale(Math.max(0, m.scale()), RoundingMode.CEILING);
        Planned p = new Planned();
        p.seq = ++seq;
        p.materialId = m.id();
        p.type = m.sourceType() == null ? "PURCHASE" : m.sourceType();
        p.qty = q;
        p.net = net;
        p.requiredDate = day;
        LocalDate release = day.minusDays(Math.max(0, m.leadTimeDays()));
        p.late = release.isBefore(in.today());
        p.releaseDate = p.late ? in.today() : release;
        planned.add(p);
        balances.add(new Balance(m.id(), day, "PLANNED", "计划订单 #" + p.seq, null, BigDecimal.ZERO, q));
        if (m.make()) {
            for (Comp c : m.comps()) {
                BigDecimal cq = q.multiply(c.qtyPer());
                if (cq.signum() <= 0) continue;
                Demand d = new Demand(c.componentId(), p.releaseDate, cq, "PARENT", null, "计划订单 #" + p.seq, m.id(), p);
                d.subs = c.subs();
                demands.computeIfAbsent(c.componentId(), k -> new ArrayList<>()).add(d);
            }
        }
        return p;
    }

    /**
     * 主料不足时按优先级用替代料的期初可用库存抵扣（替代料数量 = 主料数量 × 比例），返回仍需生成建议的主料数量。
     * 替代料尚未计算时，先为它自身已知的需求（含安全库存）保留库存，只用剩余部分；替代料不生成建议。
     */
    private BigDecimal substitute(Mat m, Demand d, BigDecimal need, LocalDate day) {
        for (MrpModel.Sub x : d.subs) {
            if (need.signum() <= 0) break;
            Mat sm = in.mats().get(x.materialId());
            if (sm == null || !sm.enabled()) continue;
            Supply opening = null;
            for (Supply s : supplies.getOrDefault(x.materialId(), List.of())) {
                if ("OPENING".equals(s.type)) opening = s;
            }
            if (opening == null || opening.remaining.signum() <= 0) continue;
            BigDecimal avail = opening.remaining;
            if (!done.contains(sm.id())) {
                BigDecimal reserved = sum(demands.getOrDefault(sm.id(), List.of()));
                if (in.includeSafety() && sm.safetyStock() != null) reserved = reserved.add(sm.safetyStock());
                avail = avail.subtract(reserved);
            }
            if (avail.signum() <= 0) continue;
            BigDecimal want = need.multiply(x.ratio());
            BigDecimal take;
            BigDecimal covered;
            if (avail.compareTo(want) >= 0) {
                take = want;
                covered = need;
            } else {
                take = avail;
                covered = avail.divide(x.ratio(), 10, RoundingMode.DOWN).stripTrailingZeros();
                if (covered.signum() <= 0) continue;
            }
            opening.remaining = opening.remaining.subtract(take);
            need = need.subtract(covered);
            String no = d.sourceNo == null ? "" : " / " + d.sourceNo;
            balances.add(new Balance(sm.id(), day, "SUBSTITUTE", "替代「" + m.code() + "」" + no, m.id(), take, BigDecimal.ZERO));
            balances.add(new Balance(m.id(), day, "SUBSTITUTE", "替代料「" + sm.code() + "」" + no, d.parentMaterialId, BigDecimal.ZERO, covered));
        }
        return need;
    }

    private void exceptionOf(Mat m, Supply s) {
        if ("OPENING".equals(s.type) || "QC".equals(s.type)) return;
        String no = s.docNo == null ? "" : s.docNo;
        int tol = in.toleranceDays();
        if (s.date.isBefore(in.today())) {
            exceptions.add(new MrpModel.Exception(m.id(), "PAST_DUE", s.docType, s.docId, s.docNo, s.lineId, s.date, in.today(), s.qty,
                    no + " 已逾期（原交期 " + s.date + "），仍有 " + plain(s.qty) + " 未到"));
            return;
        }
        if (s.firstUse == null) {
            exceptions.add(new MrpModel.Exception(m.id(), "CANCEL", s.docType, s.docId, s.docNo, s.lineId, s.date, null, s.qty,
                    no + " 数量 " + plain(s.qty) + " 在展望期内没有需求，建议取消"));
            return;
        }
        long gap = ChronoUnit.DAYS.between(s.firstUse, s.date);
        if (gap > tol) {
            exceptions.add(new MrpModel.Exception(m.id(), "EXPEDITE", s.docType, s.docId, s.docNo, s.lineId, s.date, s.firstUse, s.qty,
                    no + " 建议从 " + s.date + " 提前到 " + s.firstUse));
        } else if (-gap > tol) {
            exceptions.add(new MrpModel.Exception(m.id(), "DEFER", s.docType, s.docId, s.docNo, s.lineId, s.date, s.firstUse, s.qty,
                    no + " 建议从 " + s.date + " 推迟到 " + s.firstUse));
        }
    }

    // ==================== 工具 ====================

    private static BigDecimal sum(List<Demand> ds) {
        BigDecimal t = BigDecimal.ZERO;
        for (Demand d : ds) t = t.add(d.qty);
        return t;
    }

    private static boolean pos(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static BigDecimal ceilTo(BigDecimal q, BigDecimal lot) {
        return q.divide(lot, 0, RoundingMode.CEILING).multiply(lot);
    }

    private static BigDecimal max0(BigDecimal v) {
        return v.signum() < 0 ? BigDecimal.ZERO : v;
    }

    static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    /** 物料集合（调用方检查 BOM 引用的物料是否都已加载） */
    static Set<Long> referenced(Map<Long, Mat> mats) {
        Set<Long> out = new HashSet<>();
        for (Mat m : mats.values()) if (m.make()) for (Comp c : m.comps()) out.add(c.componentId());
        return out;
    }
}
