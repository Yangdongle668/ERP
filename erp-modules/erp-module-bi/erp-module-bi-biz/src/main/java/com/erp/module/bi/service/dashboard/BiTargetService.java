package com.erp.module.bi.service.dashboard;

import com.erp.common.exception.BizException;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.bi.api.BiErrorCodes;
import com.erp.module.bi.dal.dataobject.BiKpiTargetDO;
import com.erp.module.bi.dal.mapper.BiKpiTargetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KPI 目标达成（需求 13-02，P2）：按月维护金额类累计指标的目标（本位币），驾驶舱的 KPI 卡片在所选期间完整覆盖若干个自然月时，
 * 显示目标（各月目标之和）与达成率。目标是全公司口径，只对数据范围为“全部”的用户显示达成率。
 */
@Service
public class BiTargetService {

    /** 可设目标的指标：期间内累计的金额类指标（余额、库存、比率类不适用） */
    public static final List<String> TARGETABLE = List.of("sales_order_amount", "sales_ship_amount", "sales_receipt_amount", "gross_profit");

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    /** @param value 为空表示清除该月目标 */
    public record Item(String metricCode, int month, BigDecimal value) {
    }

    private final BiKpiTargetMapper mapper;

    public BiTargetService(BiKpiTargetMapper mapper) {
        this.mapper = mapper;
    }

    /** 某年全部目标：指标 → 12 个月（缺省为空） */
    public Map<String, BigDecimal[]> year(int year) {
        Map<String, BigDecimal[]> result = new HashMap<>();
        for (String m : TARGETABLE) result.put(m, new BigDecimal[12]);
        for (BiKpiTargetDO t : mapper.selectByMonths(year + "01", year + "12")) {
            BigDecimal[] arr = result.get(t.getMetricCode());
            if (arr != null) arr[Integer.parseInt(t.getTargetMonth().substring(4)) - 1] = t.getTargetValue();
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(int year, List<Item> items) {
        for (Item i : items == null ? List.<Item>of() : items) {
            if (!TARGETABLE.contains(i.metricCode())) throw BizException.of(BiErrorCodes.TARGET_METRIC, i.metricCode());
            if (i.month() < 1 || i.month() > 12) throw BizException.of(BiErrorCodes.QUERY_INVALID, "月份 " + i.month());
            if (i.value() != null && i.value().signum() < 0) throw new BizException(BiErrorCodes.TARGET_NEGATIVE);
            String month = String.format("%d%02d", year, i.month());
            mapper.deleteByKey(i.metricCode(), month);
            if (i.value() == null) continue;
            BiKpiTargetDO d = new BiKpiTargetDO();
            d.setMetricCode(i.metricCode());
            d.setTargetMonth(month);
            d.setTargetValue(i.value().setScale(2, RoundingMode.HALF_UP));
            mapper.insert(d);
        }
    }

    /**
     * 期间 [from, to] 的目标：期间必须由完整的自然月组成，且每个月都设置了目标才有意义（缺月视为没有目标）。
     * 不适用（非金额类指标、期间不是整月、数据范围不是“全部”、缺月）时返回 null。
     */
    public BigDecimal targetFor(String metric, LocalDate from, LocalDate to) {
        if (!TARGETABLE.contains(metric) || !SecurityUtils.currentDataScope().all()) return null;
        if (from.getDayOfMonth() != 1 || !to.equals(YearMonth.from(to).atEndOfMonth())) return null;
        YearMonth start = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        if (end.isBefore(start)) return null;
        Map<String, BigDecimal> byMonth = new HashMap<>();
        mapper.selectByMonths(start.format(MONTH), end.format(MONTH)).stream().filter(t -> t.getMetricCode().equals(metric))
                .forEach(t -> byMonth.put(t.getTargetMonth(), t.getTargetValue()));
        BigDecimal sum = BigDecimal.ZERO;
        for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            BigDecimal v = byMonth.get(m.format(MONTH));
            if (v == null) return null;
            sum = sum.add(v);
        }
        return sum.signum() > 0 ? sum : null;
    }
}
