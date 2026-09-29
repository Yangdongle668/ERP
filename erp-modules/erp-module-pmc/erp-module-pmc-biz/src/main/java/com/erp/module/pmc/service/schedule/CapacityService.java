package com.erp.module.pmc.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.pmc.api.PmcErrorCodes;
import com.erp.module.pmc.controller.vo.ScheduleVOs.CalendarDay;
import com.erp.module.pmc.controller.vo.ScheduleVOs.CalendarMonth;
import com.erp.module.pmc.controller.vo.ScheduleVOs.LoadCell;
import com.erp.module.pmc.controller.vo.ScheduleVOs.LoadDetail;
import com.erp.module.pmc.controller.vo.ScheduleVOs.LoadRow;
import com.erp.module.pmc.dal.dataobject.PmcCapacityCalendarDO;
import com.erp.module.pmc.dal.dataobject.PmcScheduleDO;
import com.erp.module.pmc.dal.mapper.PmcCapacityCalendarMapper;
import com.erp.module.pmc.dal.mapper.PmcScheduleMapper;
import com.erp.module.pmc.service.PlanningData;
import com.erp.module.pmc.service.PmcSupport;
import com.erp.module.production.api.order.OpenOrderDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 产能日历与负荷分析（需求 06-05 4.1、4.2）。没有设置例外的日期：周一至周六取工作中心日产能，周日为 0；
 * 全厂例外（工作中心 0，如节假日）对所有工作中心生效，工作中心自己的例外优先。
 */
@Service("pmcCapacityService")
public class CapacityService {

    public static final Long PLANT = 0L;
    private static final BigDecimal H3600 = new BigDecimal("3600");
    private static final BigDecimal H60 = new BigDecimal("60");

    private final PmcCapacityCalendarMapper calendarMapper;
    private final PmcScheduleMapper scheduleMapper;
    private final PlanningData data;
    private final PmcSupport support;

    public CapacityService(PmcCapacityCalendarMapper calendarMapper, PmcScheduleMapper scheduleMapper, PlanningData data, PmcSupport support) {
        this.calendarMapper = calendarMapper;
        this.scheduleMapper = scheduleMapper;
        this.data = data;
        this.support = support;
    }

    /** 某段日期的产能日历（一次读取，供排产、负荷分析使用） */
    public final class Calendar {
        private final Map<String, PmcCapacityCalendarDO> exceptions = new HashMap<>();
        private final Map<Long, WorkCenterDTO> wcs;

        Calendar(LocalDate from, LocalDate to, Map<Long, WorkCenterDTO> wcs) {
            this.wcs = wcs;
            for (PmcCapacityCalendarDO c : calendarMapper.selectList(new LambdaQueryWrapper<PmcCapacityCalendarDO>()
                    .ge(PmcCapacityCalendarDO::getCalDate, from).le(PmcCapacityCalendarDO::getCalDate, to))) {
                exceptions.put(c.getWorkCenterId() + "|" + c.getCalDate(), c);
            }
        }

        public BigDecimal defaultHours(Long wcId, LocalDate d) {
            WorkCenterDTO w = wcs.get(wcId);
            if (w == null || d.getDayOfWeek() == DayOfWeek.SUNDAY) return BigDecimal.ZERO;
            return PmcSupport.nz(w.capacityHoursPerDay());
        }

        public PmcCapacityCalendarDO exception(Long wcId, LocalDate d) {
            PmcCapacityCalendarDO own = exceptions.get(wcId + "|" + d);
            return own != null ? own : exceptions.get(PLANT + "|" + d);
        }

        public BigDecimal hours(Long wcId, LocalDate d) {
            PmcCapacityCalendarDO e = exception(wcId, d);
            return e != null ? e.getAvailableHours() : defaultHours(wcId, d);
        }

        public Map<Long, WorkCenterDTO> workCenters() {
            return wcs;
        }
    }

    public Calendar calendar(LocalDate from, LocalDate to) {
        return new Calendar(from, to, data.workCenters());
    }

    // ==================== 产能日历 ====================

    public CalendarMonth month(Long workCenterId, String month) {
        YearMonth ym = StringUtils.hasText(month) ? YearMonth.parse(month) : YearMonth.now();
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        Long wc = workCenterId == null ? PLANT : workCenterId;
        Calendar cal = calendar(from, to);
        Map<LocalDate, BigDecimal> loads = PLANT.equals(wc) ? Map.of() : dayLoads(wc, from, to);
        List<CalendarDay> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            PmcCapacityCalendarDO own = cal.exceptions.get(wc + "|" + d);
            PmcCapacityCalendarDO e = PLANT.equals(wc) ? own : cal.exception(wc, d);
            BigDecimal def = PLANT.equals(wc) ? (d.getDayOfWeek() == DayOfWeek.SUNDAY ? BigDecimal.ZERO : null) : cal.defaultHours(wc, d);
            BigDecimal avail = e != null ? e.getAvailableHours() : def;
            BigDecimal load = loads.getOrDefault(d, BigDecimal.ZERO);
            BigDecimal rate = avail == null || avail.signum() == 0 ? null : load.divide(avail, 4, RoundingMode.HALF_UP);
            days.add(new CalendarDay(d, avail, def, e != null, e == null ? null : e.getReason(), load, rate));
        }
        WorkCenterDTO w = cal.workCenters().get(wc);
        return new CalendarMonth(wc, PLANT.equals(wc) ? "全厂" : w == null ? null : w.name(), ym.toString(), days);
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(Long workCenterId, LocalDate date, BigDecimal hours, String reason) {
        batch(workCenterId, date, date, hours, reason);
    }

    /** 批量设置（如“国庆 10-01～10-07 停工”）；hours 为空表示恢复默认 */
    @Transactional(rollbackFor = Exception.class)
    public int batch(Long workCenterId, LocalDate from, LocalDate to, BigDecimal hours, String reason) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 366) {
            throw new BizException(PmcErrorCodes.CALENDAR_RANGE_INVALID);
        }
        if (hours != null && (hours.signum() < 0 || hours.compareTo(new BigDecimal("24")) > 0)) throw new BizException(PmcErrorCodes.CALENDAR_HOURS_INVALID);
        Long wc = workCenterId == null ? PLANT : workCenterId;
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            PmcCapacityCalendarDO c = calendarMapper.selectOne(new LambdaQueryWrapper<PmcCapacityCalendarDO>()
                    .eq(PmcCapacityCalendarDO::getWorkCenterId, wc).eq(PmcCapacityCalendarDO::getCalDate, d));
            if (hours == null) {
                if (c != null) calendarMapper.deleteById(c.getId());
            } else if (c == null) {
                c = new PmcCapacityCalendarDO();
                c.setWorkCenterId(wc);
                c.setCalDate(d);
                c.setAvailableHours(hours);
                c.setReason(PmcSupport.trim(reason));
                calendarMapper.insert(c);
            } else {
                c.setAvailableHours(hours);
                c.setReason(PmcSupport.trim(reason));
                calendarMapper.updateByIdOrFail(c);
            }
            n++;
        }
        return n;
    }

    // ==================== 负荷 ====================

    /** 工序负荷 = 准备 + 剩余数量 × 标准工时（小时） */
    public static BigDecimal loadHours(BigDecimal remainingQty, BigDecimal setupMinutes, BigDecimal runSeconds) {
        BigDecimal run = PmcSupport.nz(runSeconds).multiply(PmcSupport.max0(remainingQty)).divide(H3600, 4, RoundingMode.HALF_UP);
        BigDecimal setup = remainingQty.signum() > 0 ? PmcSupport.nz(setupMinutes).divide(H60, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return run.add(setup).setScale(2, RoundingMode.HALF_UP);
    }

    static Map<LocalDate, BigDecimal> parse(String dayLoads) {
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        if (!StringUtils.hasText(dayLoads)) return map;
        for (String part : dayLoads.split(";")) {
            String[] kv = part.split("=");
            if (kv.length == 2) map.merge(LocalDate.parse(kv[0]), new BigDecimal(kv[1]), BigDecimal::add);
        }
        return map;
    }

    static String format(Map<LocalDate, BigDecimal> loads) {
        return loads.entrySet().stream().filter(e -> e.getValue().signum() > 0)
                .map(e -> e.getKey() + "=" + e.getValue().setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(";"));
    }

    private Map<LocalDate, BigDecimal> dayLoads(Long wcId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> map = new HashMap<>();
        for (Item i : items(from, to)) {
            if (!wcId.equals(i.wcId)) continue;
            i.loads.forEach((d, h) -> {
                if (!d.isBefore(from) && !d.isAfter(to)) map.merge(d, h, BigDecimal::add);
            });
        }
        return map;
    }

    /** 一道工序的按日负荷 */
    private record Item(Long wcId, Long orderId, String orderNo, Long materialId, int seq, String operation, BigDecimal hours,
                        Map<LocalDate, BigDecimal> loads, boolean scheduled) {
    }

    /**
     * 负荷来源：有排产结果的订单取排产的按日占用；没有的按计划开工～完工在工作日上平均分摊（无限产能）。
     */
    private List<Item> items(LocalDate from, LocalDate to) {
        List<Item> out = new ArrayList<>();
        List<PmcScheduleDO> sched = scheduleMapper.selectList(new LambdaQueryWrapper<PmcScheduleDO>());
        Set<Long> scheduledOrders = sched.stream().map(PmcScheduleDO::getProdOrderId).collect(Collectors.toSet());
        for (PmcScheduleDO s : sched) {
            out.add(new Item(s.getWorkCenterId(), s.getProdOrderId(), s.getProdOrderNo(), s.getMaterialId(), s.getOperationSeq(), s.getOperation(),
                    s.getLoadHours(), parse(s.getDayLoads()), true));
        }
        Calendar cal = calendar(from.minusDays(400), to.plusDays(400));
        for (OpenOrderDTO o : data.productionQueryApi().getOpenOrders(null)) {
            if (scheduledOrders.contains(o.id()) || o.planEnd().isBefore(from) || o.planStart().isAfter(to)) continue;
            for (OpenOrderDTO.Operation op : o.operations()) {
                if (op.workCenterId() == null) continue;
                BigDecimal h = loadHours(o.qty().subtract(op.doneQty()), op.stdSetupMinutes(), op.stdRunSeconds());
                if (h.signum() <= 0) continue;
                List<LocalDate> days = new ArrayList<>();
                for (LocalDate d = o.planStart(); !d.isAfter(o.planEnd()); d = d.plusDays(1)) {
                    if (cal.hours(op.workCenterId(), d).signum() > 0) days.add(d);
                }
                if (days.isEmpty()) days.add(o.planEnd());
                BigDecimal per = h.divide(BigDecimal.valueOf(days.size()), 4, RoundingMode.HALF_UP);
                Map<LocalDate, BigDecimal> loads = new LinkedHashMap<>();
                days.forEach(d -> loads.put(d, per));
                out.add(new Item(op.workCenterId(), o.id(), o.docNo(), o.materialId(), op.seq(), op.operation(), h, loads, false));
            }
        }
        return out;
    }

    /** 负荷分析：行 = 工作中心，列 = 日期 */
    public List<LoadRow> load(LocalDate from, LocalDate to, Long deptId) {
        LocalDate f = from == null ? LocalDate.now() : from;
        LocalDate t = to == null ? f.plusDays(13) : to;
        if (t.isBefore(f) || ChronoUnit.DAYS.between(f, t) > 92) throw new BizException(PmcErrorCodes.CALENDAR_RANGE_INVALID);
        Calendar cal = calendar(f, t);
        Set<Long> depts = deptId == null ? null : support.deptAndChildren(deptId);
        Map<Long, Map<LocalDate, BigDecimal>> byWc = new HashMap<>();
        for (Item i : items(f, t)) {
            if (i.wcId == null) continue;
            Map<LocalDate, BigDecimal> m = byWc.computeIfAbsent(i.wcId, k -> new HashMap<>());
            i.loads.forEach((d, h) -> {
                if (!d.isBefore(f) && !d.isAfter(t)) m.merge(d, h, BigDecimal::add);
            });
        }
        List<LoadRow> rows = new ArrayList<>();
        List<WorkCenterDTO> wcs = new ArrayList<>(cal.workCenters().values());
        wcs.sort(Comparator.comparing(WorkCenterDTO::code));
        for (WorkCenterDTO w : wcs) {
            if (depts != null && !depts.contains(w.deptId())) continue;
            Map<LocalDate, BigDecimal> m = byWc.getOrDefault(w.id(), Map.of());
            List<LoadCell> cells = new ArrayList<>();
            BigDecimal tl = BigDecimal.ZERO;
            BigDecimal tc = BigDecimal.ZERO;
            for (LocalDate d = f; !d.isAfter(t); d = d.plusDays(1)) {
                BigDecimal load = m.getOrDefault(d, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                BigDecimal cap = cal.hours(w.id(), d);
                tl = tl.add(load);
                tc = tc.add(cap);
                BigDecimal rate = cap.signum() == 0 ? null : load.divide(cap, 4, RoundingMode.HALF_UP);
                cells.add(new LoadCell(d, load, cap, rate, load.compareTo(cap) > 0));
            }
            rows.add(new LoadRow(w.id(), w.code(), w.name(), w.deptId(), tl, tc, cells));
        }
        return rows;
    }

    public List<LoadDetail> loadDetail(Long workCenterId, LocalDate date) {
        List<Item> list = items(date, date).stream().filter(i -> workCenterId.equals(i.wcId) && i.loads.containsKey(date)).toList();
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(Item::materialId).toList());
        return list.stream().map(i -> {
            MaterialDTO m = ms.get(i.materialId);
            return new LoadDetail(i.orderId, i.orderNo, m == null ? null : m.code(), m == null ? null : m.name(), i.seq, i.operation,
                    i.loads.get(date).setScale(2, RoundingMode.HALF_UP), i.scheduled);
        }).toList();
    }
}
