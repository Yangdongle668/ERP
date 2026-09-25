package com.erp.module.engineering.api.routing;

import java.math.BigDecimal;

/**
 * 工作中心（需求 05-04）。capacityHoursPerDay = 每班小时 × 班次 × 效率。
 *
 * @param wcType LINE/MACHINE/MANUAL/OUTSOURCE
 */
public record WorkCenterDTO(Long id, String code, String name, Long deptId, String wcType, BigDecimal hoursPerShift, int shiftCount,
                            BigDecimal efficiencyPct, BigDecimal capacityHoursPerDay, BigDecimal laborRate, BigDecimal overheadRate,
                            boolean enabled) {
}
