package com.erp.module.bi.service.forecast;

import java.util.Arrays;

/**
 * 月度出货量预测模型（需求 13-04 2.4）：线性趋势（最小二乘，取最近 24 个月）× 季节指数（有满 24 个月历史时按日历月计算，否则不用）。
 * 历史不足 12 个月不给预测。纯计算、无外部依赖，便于验证。
 */
public final class ForecastModel {

    public static final int MIN_HISTORY = 12;
    private static final int WINDOW = 24;

    private ForecastModel() {
    }

    /**
     * @param months 最近 n 个月的出货量（时间升序，最后一个为最近完整月）
     * @param lastMonth 最后一个月的日历月（1～12）
     * @param horizon 预测月数
     * @param seasonal 历史满 24 个月时是否使用季节指数
     */
    public record Result(double[] forecast, boolean seasonalUsed, Double mape) {
    }

    public static Result forecast(double[] months, int lastMonth, int horizon) {
        if (months.length < MIN_HISTORY) throw new IllegalArgumentException("历史数据不足 " + MIN_HISTORY + " 个月");
        double[] h = months.length > WINDOW ? Arrays.copyOfRange(months, months.length - WINDOW, months.length) : months;
        boolean seasonal = h.length >= WINDOW;
        double[] fc = project(h, lastMonth, horizon, seasonal);
        return new Result(fc, seasonal, backtest(h, lastMonth, seasonal));
    }

    /** 回测：用除最近 3 个月外的历史预测最近 3 个月，返回平均绝对百分比误差（实际为 0 的月份不计）；历史不足 15 个月时为空 */
    private static Double backtest(double[] h, int lastMonth, boolean seasonal) {
        if (h.length < MIN_HISTORY + 3) return null;
        double[] train = Arrays.copyOfRange(h, 0, h.length - 3);
        int trainLast = ((lastMonth - 3 - 1) % 12 + 12) % 12 + 1;
        double[] p = project(train, trainLast, 3, seasonal && train.length >= WINDOW);
        double sum = 0;
        int n = 0;
        for (int i = 0; i < 3; i++) {
            double actual = h[h.length - 3 + i];
            if (actual <= 0) continue;
            sum += Math.abs(p[i] - actual) / actual;
            n++;
        }
        return n == 0 ? null : sum / n * 100;
    }

    private static double[] project(double[] h, int lastMonth, int horizon, boolean seasonal) {
        int n = h.length;
        double[] index = new double[12];
        Arrays.fill(index, 1.0);
        double a = 0;
        double b = 0;
        // 交替估计：用季节指数去季节化后拟合趋势，再用趋势线求各日历月的比值（迭代 5 次即收敛），避免季节形态被误当成趋势
        for (int iter = 0; iter < (seasonal ? 5 : 1); iter++) {
            double[] adj = new double[n];
            for (int t = 0; t < n; t++) adj[t] = h[t] / index[calendar(lastMonth, n, t)];
            double[] line = ols(adj);
            a = line[0];
            b = line[1];
            if (!seasonal) break;
            double[] ratioSum = new double[12];
            int[] cnt = new int[12];
            for (int t = 0; t < n; t++) {
                double fit = a + b * t;
                if (fit <= 0) continue;
                int cal = calendar(lastMonth, n, t);
                ratioSum[cal] += h[t] / fit;
                cnt[cal]++;
            }
            double total = 0;
            int used = 0;
            double[] next = new double[12];
            for (int k = 0; k < 12; k++) {
                if (cnt[k] > 0) {
                    next[k] = ratioSum[k] / cnt[k];
                    total += next[k];
                    used++;
                }
            }
            if (used == 0 || total <= 0) break;
            // 归一化使平均为 1，缺数据的月份取 1
            double mean = total / used;
            for (int k = 0; k < 12; k++) index[k] = cnt[k] > 0 ? next[k] / mean : 1.0;
        }
        double[] out = new double[horizon];
        for (int i = 0; i < horizon; i++) {
            int t = n + i;
            out[i] = Math.max(0, (a + b * t) * index[((lastMonth - 1 + i + 1) % 12 + 12) % 12]);
        }
        return out;
    }

    /** 第 t 个历史月（0 起）的日历月下标（0～11） */
    private static int calendar(int lastMonth, int n, int t) {
        return ((lastMonth - 1 - (n - 1 - t)) % 12 + 12) % 12;
    }

    /** 最小二乘直线 y = a + b·t，返回 {a, b} */
    private static double[] ols(double[] y) {
        int n = y.length;
        double st = 0, sy = 0, stt = 0, sty = 0;
        for (int t = 0; t < n; t++) {
            st += t;
            sy += y[t];
            stt += (double) t * t;
            sty += t * y[t];
        }
        double denom = n * stt - st * st;
        double b = denom == 0 ? 0 : (n * sty - st * sy) / denom;
        return new double[]{(sy - b * st) / n, b};
    }
}
