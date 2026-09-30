package com.erp.module.bi;

import com.erp.module.bi.service.forecast.ForecastModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForecastModelTest {

    @Test
    void needsTwelveMonths() {
        assertThatThrownBy(() -> ForecastModel.forecast(new double[11], 6, 3)).hasMessageContaining("历史数据不足");
    }

    @Test
    void linearTrendIsExtrapolated() {
        double[] h = new double[12];
        for (int i = 0; i < 12; i++) h[i] = 100 + 10 * i; // 100…210
        ForecastModel.Result r = ForecastModel.forecast(h, 12, 3);
        assertThat(r.seasonalUsed()).isFalse();
        assertThat(r.forecast()).containsExactly(new double[]{220, 230, 240}, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(r.mape()).isNull();
    }

    @Test
    void flatSeriesAndNonNegative() {
        double[] flat = new double[12];
        java.util.Arrays.fill(flat, 50);
        assertThat(ForecastModel.forecast(flat, 3, 2).forecast()).containsExactly(new double[]{50, 50}, org.assertj.core.data.Offset.offset(1e-6));
        double[] down = new double[12];
        for (int i = 0; i < 12; i++) down[i] = 120 - 10 * i; // 走到接近 0
        for (double v : ForecastModel.forecast(down, 12, 6).forecast()) assertThat(v).isGreaterThanOrEqualTo(0);
    }

    /** 24 个月：基础量 100 + 季节波动（第 12 月 ×1.5，第 1 月 ×0.5），预测应保持季节形态，回测误差很小 */
    @Test
    void seasonalPatternIsKept() {
        double[] h = new double[24];
        for (int t = 0; t < 24; t++) {
            int cal = t % 12 + 1; // t=0 为 1 月，t=23 为 12 月
            double season = cal == 12 ? 1.5 : cal == 1 ? 0.5 : 1.0;
            h[t] = 100 * season;
        }
        ForecastModel.Result r = ForecastModel.forecast(h, 12, 3); // 预测次年 1、2、3 月
        assertThat(r.seasonalUsed()).isTrue();
        assertThat(r.forecast()[0]).isLessThan(r.forecast()[1] * 0.75);      // 1 月明显低于 2 月
        assertThat(r.forecast()[1]).isBetween(85.0, 115.0);
        assertThat(r.mape()).isNotNull();
        assertThat(r.mape()).isLessThan(15.0);
    }
}
