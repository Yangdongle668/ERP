package com.erp.module.quality.service.basic;

import java.util.List;
import java.util.Map;

/**
 * GB/T 2828.1（ISO 2859-1）一次正常检验：样本量字码表（表 1）与主表（表 2-A），箭头规则已展开（10-01 第 2、3 节）。
 *
 * <p>主表的对角结构：字码序号 i（A=0 … R=15）与 AQL 序号 j（0.010=0 … 10=15）之和 s 决定判定组：
 * s=14 为 Ac0/Re1；s=15 为向上箭头（取上一行的 0/1）；s=16 为向下箭头（取下一行的 1/2）；s=17～24 依次为
 * 1/2、2/3、3/4、5/6、7/8、10/11、14/15、21/22；s≤13 向下取第一个 0/1；s>24 向上取 21/22。箭头指向的方案同时改变样本量。
 */
public final class AqlTable {

    private AqlTable() {
    }

    public static final List<String> LEVELS = List.of("S1", "S2", "S3", "S4", "I", "II", "III");

    /** 支持的 AQL 系列（0 表示零缺陷：Ac=0、Re=1） */
    public static final List<String> AQLS = List.of("0.010", "0.015", "0.025", "0.040", "0.065", "0.10", "0.15", "0.25", "0.40", "0.65",
            "1.0", "1.5", "2.5", "4.0", "6.5", "10");

    private static final String LETTERS = "ABCDEFGHJKLMNPQR";
    private static final int[] SAMPLE = {2, 3, 5, 8, 13, 20, 32, 50, 80, 125, 200, 315, 500, 800, 1250, 2000};
    private static final long[] LOT_MAX = {8, 15, 25, 50, 90, 150, 280, 500, 1200, 3200, 10000, 35000, 150000, 500000, Long.MAX_VALUE};
    /** 行：批量范围；列：S1 S2 S3 S4 I II III */
    private static final String[] CODE = {
            "AAAAAAB", "AAAAABC", "AABBBCD", "ABBCCDE", "BBCCCEF", "BBCDDFG", "BCDEEGH", "BCDEFHJ",
            "CCEFGJK", "CDEGHKL", "CDFGJLM", "CDFHKMN", "DEGJLNP", "DEGJMPQ", "DEHKNQR"};
    private static final int[][] PAIRS = {{1, 2}, {2, 3}, {3, 4}, {5, 6}, {7, 8}, {10, 11}, {14, 15}, {21, 22}};

    /** 一个等级的抽样结果 */
    public record Plan(String letter, int n, int ac, int re) {
    }

    /** 字码 */
    public static char codeLetter(long lotQty, String level) {
        int col = LEVELS.indexOf(level);
        if (col < 0) throw new IllegalArgumentException("检验水平不正确：" + level);
        long lot = Math.max(lotQty, 2);
        for (int r = 0; r < LOT_MAX.length; r++) {
            if (lot <= LOT_MAX[r]) return CODE[r].charAt(col);
        }
        return CODE[CODE.length - 1].charAt(col);
    }

    public static boolean validAql(String aql) {
        return aql != null && (isZero(aql) || aqlIndex(aql) >= 0);
    }

    static boolean isZero(String aql) {
        try {
            return new java.math.BigDecimal(aql.trim()).signum() == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int aqlIndex(String aql) {
        java.math.BigDecimal v;
        try {
            v = new java.math.BigDecimal(aql.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
        for (int j = 0; j < AQLS.size(); j++) {
            if (new java.math.BigDecimal(AQLS.get(j)).compareTo(v) == 0) return j;
        }
        return -1;
    }

    /** 批量 + 检验水平 + AQL → 样本量与 Ac/Re（未截断到批量） */
    public static Plan lookup(long lotQty, String level, String aql) {
        char letter = codeLetter(lotQty, level);
        int i = LETTERS.indexOf(letter);
        if (isZero(aql)) return new Plan(String.valueOf(letter), SAMPLE[i], 0, 1);
        int j = aqlIndex(aql);
        if (j < 0) throw new IllegalArgumentException("AQL 不正确：" + aql);
        int s = i + j;
        if (s <= 13) {
            i = 14 - j;
        } else if (s == 15) {
            i = i - 1 >= 0 ? i - 1 : i + 2;
        } else if (s == 16) {
            i = i + 1 <= 15 ? i + 1 : i - 2;
        } else if (s > 24) {
            i = Math.max(0, 24 - j);
        }
        i = Math.max(0, Math.min(15, i));
        int k = i + j;
        int ac;
        int re;
        if (k <= 15) {
            ac = 0;
            re = 1;
        } else {
            int idx = Math.min(PAIRS.length - 1, Math.max(0, k - 17));
            ac = PAIRS[idx][0];
            re = PAIRS[idx][1];
        }
        return new Plan(String.valueOf(LETTERS.charAt(i)), SAMPLE[i], ac, re);
    }

    /** 方便测试：各等级的计算结果 */
    public static Map<String, Plan> lookupAll(long lotQty, String level, String cr, String ma, String mi) {
        java.util.LinkedHashMap<String, Plan> m = new java.util.LinkedHashMap<>();
        if (cr != null && !cr.isBlank()) m.put("CR", lookup(lotQty, level, cr));
        if (ma != null && !ma.isBlank()) m.put("MA", lookup(lotQty, level, ma));
        if (mi != null && !mi.isBlank()) m.put("MI", lookup(lotQty, level, mi));
        return m;
    }
}
