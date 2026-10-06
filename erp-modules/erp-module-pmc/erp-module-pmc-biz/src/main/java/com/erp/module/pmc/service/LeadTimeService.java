package com.erp.module.pmc.service;

import com.erp.module.engineering.api.routing.RoutingDTO;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.pmc.config.PmcModuleConfig;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 生产提前期（需求 06-03 3.2 节 P2）：参数 pmc.lead-time.basis = ROUTING 时，有默认工艺路线的自制件按
 * “Σ准备时间 + 数量 × Σ标准工时”逐道工序除以工作中心日产能（小时 / 天）换算为天数（向上取整，至少 1 天）；
 * 参数为 MATERIAL 或没有工艺路线时取物料的生产提前期（天）。
 */
@Service("pmcLeadTimeService")
public class LeadTimeService {

    public static final String MATERIAL = "MATERIAL";
    public static final String ROUTING = "ROUTING";
    /** 工作中心未维护日产能时按 8 小时 / 天 */
    private static final BigDecimal DEFAULT_HOURS = BigDecimal.valueOf(8);

    /** 工艺换算：fixedDays = Σ准备时间 ÷ 日产能，daysPerUnit = Σ标准工时 ÷ 日产能 */
    public record RoutingTime(BigDecimal fixedDays, BigDecimal daysPerUnit) {

        public int days(BigDecimal qty) {
            BigDecimal d = fixedDays.add(daysPerUnit.multiply(qty == null ? BigDecimal.ONE : qty.max(BigDecimal.ZERO)));
            return Math.max(1, d.setScale(0, RoundingMode.CEILING).intValue());
        }
    }

    private final PlanningData data;
    private final PmcSupport support;

    public LeadTimeService(PlanningData data, PmcSupport support) {
        this.data = data;
        this.support = support;
    }

    public boolean routingBasis() {
        return ROUTING.equals(support.params().getString(PmcModuleConfig.P_LEAD_TIME_BASIS));
    }

    /** 参数为按工艺时，各物料默认工艺路线的换算（没有工艺路线或工时全为 0 的物料不在结果中） */
    public Map<Long, RoutingTime> routingTimes(Collection<Long> materialIds) {
        Map<Long, RoutingTime> out = new HashMap<>();
        if (!routingBasis() || materialIds == null || materialIds.isEmpty()) return out;
        Map<Long, WorkCenterDTO> wcs = data.workCenters();
        for (Long id : materialIds) {
            Optional<RoutingDTO> r = data.routingApi().getDefaultRouting(id);
            r.map(x -> convert(x, wcs)).ifPresent(t -> out.put(id, t));
        }
        return out;
    }

    /** 自制件的生产提前期（天）：按工艺换算，否则 fallbackDays */
    public int makeLeadDays(Long materialId, BigDecimal qty, int fallbackDays) {
        RoutingTime t = routingTimes(java.util.List.of(materialId)).get(materialId);
        return t == null ? fallbackDays : t.days(qty);
    }

    static RoutingTime convert(RoutingDTO r, Map<Long, WorkCenterDTO> wcs) {
        BigDecimal fixed = BigDecimal.ZERO;
        BigDecimal perUnit = BigDecimal.ZERO;
        for (RoutingDTO.Step s : r.steps()) {
            WorkCenterDTO w = s.workCenterId() == null ? null : wcs.get(s.workCenterId());
            BigDecimal hours = w != null && w.capacityHoursPerDay() != null && w.capacityHoursPerDay().signum() > 0 ? w.capacityHoursPerDay() : DEFAULT_HOURS;
            fixed = fixed.add(PmcSupport.nz(s.setupMinutes()).divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP).divide(hours, 10, RoundingMode.HALF_UP));
            perUnit = perUnit.add(PmcSupport.nz(s.runSeconds()).divide(BigDecimal.valueOf(3600), 10, RoundingMode.HALF_UP).divide(hours, 10, RoundingMode.HALF_UP));
        }
        if (fixed.signum() == 0 && perUnit.signum() == 0) return null;
        return new RoutingTime(fixed, perUnit);
    }
}
