package com.erp.module.pmc.api.shipping;

import java.math.BigDecimal;
import java.util.List;

/** 出货计划（出货模块调用） */
public interface ShippingPlanApi {

    /** 某周已发布出货计划的未取消行；week 为 ISO 周，如 2026-W40 */
    List<ShippingPlanLineDTO> getPlanLines(String week);

    /** 出货通知生成 / 作废后回写已通知数量（delta 可为负） */
    void onNoticed(Long planLineId, BigDecimal deltaQty);
}
