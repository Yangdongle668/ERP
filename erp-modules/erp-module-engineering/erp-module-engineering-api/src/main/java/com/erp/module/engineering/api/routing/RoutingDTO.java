package com.erp.module.engineering.api.routing;

import java.math.BigDecimal;
import java.util.List;

/** 工艺路线（已审核版本）及工序 */
public record RoutingDTO(Long id, String docNo, Long materialId, int version, boolean isDefault, List<Step> steps) {

    /**
     * @param setupMinutes 准备时间（分钟/批）
     * @param runSeconds   标准工时（秒/件）
     */
    public record Step(int seq, String operation, Long workCenterId, BigDecimal setupMinutes, BigDecimal runSeconds, boolean reportPoint,
                       boolean inspectionPoint, boolean outsourced, String remark) {
    }
}
