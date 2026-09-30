package com.erp.module.sales.api.forecast;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 销售预测（PMC 使用）。 */
public interface ForecastApi {

    /**
     * 已发布预测在 [fromPeriod, toPeriod]（yyyyMM）内净数量 &gt; 0 的行；已过去的月份（早于当前月）不返回（SAL-FC-R04）。
     */
    List<NetForecastDTO> getNetForecast(String fromPeriod, String toPeriod);

    /**
     * @param quantities 物料 ID → 各月预测数量（键 yyyyMM）
     */
    record DraftRequest(String title, String startPeriod, String endPeriod, String remark, Map<Long, Map<String, BigDecimal>> quantities) {
    }

    /**
     * 生成销售预测草稿（BI 销售预测建议一键生成）：由当前用户创建，草稿状态，业务 / 计划员确认后发布。
     * 期间不能早于当前月、跨度 ≤ 12 个月，与手工新建相同。
     *
     * @return 预测单 ID
     */
    Long createDraft(DraftRequest request);
}
