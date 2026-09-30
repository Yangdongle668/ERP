package com.erp.module.production.api.cost;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 成本核算用生产数据（财务 12-07）：来源单据 → 生产订单、订单信息、期间报工工时 */
public interface ProductionCostApi {

    /** 领料单 / 退料单 / 完工入库单的单据类型（库存流水的 source_type） */
    String SOURCE_ISSUE = "MFG_ISSUE";
    String SOURCE_RETURN = "MFG_RETURN";
    String SOURCE_FINISH = "MFG_FINISH";

    /**
     * @param orderType  NORMAL / SAMPLE / REWORK / DISASSEMBLY
     * @param prodStatus 生产状态（COMPLETED / CLOSED 表示已完工，不再有在制）
     * @param deptId     生产车间
     */
    record CostOrder(Long prodOrderId, String docNo, Long materialId, String orderType, String prodStatus, BigDecimal planQty, Long deptId) {
    }

    /** 已审核报工的工时（按订单 + 车间汇总） */
    record WorkHour(Long prodOrderId, Long deptId, BigDecimal workHours, BigDecimal goodQty) {
    }

    /** 来源单据 ID → 生产订单 ID（sourceType 为 SOURCE_ISSUE / SOURCE_RETURN / SOURCE_FINISH） */
    Map<Long, Long> getOrderIdsBySource(String sourceType, Collection<Long> sourceIds);

    List<CostOrder> getOrders(Collection<Long> prodOrderIds);

    List<WorkHour> getWorkHours(LocalDate from, LocalDate to);
}
