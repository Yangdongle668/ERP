package com.erp.module.workbench.api.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * 卡片数据。
 *
 * @param value        主数值
 * @param unit         AMOUNT（本位币金额）/ COUNT / PERCENT
 * @param changePct    对比变化百分比（环比 / 同比，可空），如 12.5 表示上升 12.5%
 * @param compareLabel 对比说明，如“环比”
 * @param costLike     成本类指标（上升显示红色）
 * @param subText      辅助说明，如“5 家客户”
 * @param series       CHART 类卡片的数据点
 */
public record CardData(BigDecimal value, String unit, BigDecimal changePct, String compareLabel, boolean costLike, String subText,
                       List<CardPoint> series) {

    public static CardData amount(BigDecimal value, String subText) {
        return new CardData(value, "AMOUNT", null, null, false, subText, List.of());
    }

    public static CardData count(long value, String subText) {
        return new CardData(BigDecimal.valueOf(value), "COUNT", null, null, false, subText, List.of());
    }

    public static CardData percent(BigDecimal value, String subText) {
        return new CardData(value, "PERCENT", null, null, false, subText, List.of());
    }

    public static CardData chart(List<CardPoint> series) {
        return new CardData(null, "AMOUNT", null, null, false, null, series);
    }

    /** 与上期比较：previous 为 0 或空时不显示变化 */
    public CardData compare(BigDecimal previous, String label) {
        BigDecimal pct = previous == null || previous.signum() == 0 || value == null ? null
                : value.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 1, java.math.RoundingMode.HALF_UP);
        return new CardData(value, unit, pct, label, costLike, subText, series);
    }

    public CardData asCost() {
        return new CardData(value, unit, changePct, compareLabel, true, subText, series);
    }

    /** @param label 横轴标签（如 2026-09） */
    public record CardPoint(String label, BigDecimal value) {
    }
}
