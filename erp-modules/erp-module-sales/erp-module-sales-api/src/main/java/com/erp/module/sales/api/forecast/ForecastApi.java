package com.erp.module.sales.api.forecast;

import java.util.List;

/** 销售预测（PMC 使用）。 */
public interface ForecastApi {

    /**
     * 已发布预测在 [fromPeriod, toPeriod]（yyyyMM）内净数量 &gt; 0 的行；已过去的月份（早于当前月）不返回（SAL-FC-R04）。
     */
    List<NetForecastDTO> getNetForecast(String fromPeriod, String toPeriod);
}
