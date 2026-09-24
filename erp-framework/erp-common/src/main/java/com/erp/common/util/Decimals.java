package com.erp.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 数量、单价、金额的精度约定（见需求文档 00 第 4.6、4.7 节）。
 *
 * <p>ERP 中所有数量和金额一律使用 {@link BigDecimal}，禁止 double/float。
 */
public final class Decimals {

    public static final int QTY_SCALE = 4;
    public static final int PRICE_SCALE = 6;
    public static final int AMOUNT_SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Decimals() {
    }

    public static BigDecimal qty(BigDecimal value) {
        return scale(value, QTY_SCALE);
    }

    public static BigDecimal price(BigDecimal value) {
        return scale(value, PRICE_SCALE);
    }

    public static BigDecimal amount(BigDecimal value) {
        return scale(value, AMOUNT_SCALE);
    }

    /** 金额 = 数量 × 单价，结果按金额精度舍入。 */
    public static BigDecimal multiplyAmount(BigDecimal qty, BigDecimal price) {
        return amount(nullToZero(qty).multiply(nullToZero(price)));
    }

    public static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, ROUNDING);
    }
}
