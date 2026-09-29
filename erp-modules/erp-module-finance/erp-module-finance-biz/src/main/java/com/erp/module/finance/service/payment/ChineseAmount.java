package com.erp.module.finance.service.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 金额中文大写（付款申请单打印），如 10735.50 → 壹万零柒佰叁拾伍元伍角整 */
public final class ChineseAmount {

    private static final String[] DIGITS = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    private static final String[] UNITS = {"", "拾", "佰", "仟"};
    private static final String[] SECTIONS = {"", "万", "亿", "万亿"};

    private ChineseAmount() {
    }

    public static String of(BigDecimal amount) {
        if (amount == null) return "";
        BigDecimal v = amount.abs().setScale(2, RoundingMode.HALF_UP);
        long fen = v.movePointRight(2).longValueExact();
        long yuan = fen / 100;
        int jiao = (int) (fen / 10 % 10);
        int f = (int) (fen % 10);
        StringBuilder sb = new StringBuilder(amount.signum() < 0 ? "负" : "");
        if (yuan > 0) sb.append(integer(yuan)).append("元");
        if (jiao == 0 && f == 0) {
            if (yuan == 0) sb.append("零元");
            return sb.append("整").toString();
        }
        if (jiao > 0) sb.append(DIGITS[jiao]).append("角");
        else if (yuan > 0) sb.append("零");
        if (f > 0) sb.append(DIGITS[f]).append("分");
        else sb.append("整");
        return sb.toString();
    }

    private static String integer(long n) {
        StringBuilder result = new StringBuilder();
        int section = 0;
        boolean needZero = false;
        while (n > 0) {
            int part = (int) (n % 10000);
            if (part > 0) {
                String text = section(part) + SECTIONS[section];
                if (needZero) text = text + "零";
                result.insert(0, text);
                needZero = part < 1000;
            } else if (result.length() > 0) {
                needZero = true;
            }
            n /= 10000;
            section++;
        }
        String s = result.toString();
        while (s.startsWith("零")) s = s.substring(1);
        while (s.endsWith("零")) s = s.substring(0, s.length() - 1);
        return s.replace("零零", "零");
    }

    private static String section(int part) {
        StringBuilder sb = new StringBuilder();
        boolean zero = false;
        for (int i = 3; i >= 0; i--) {
            int d = part / (int) Math.pow(10, i) % 10;
            if (d == 0) {
                if (sb.length() > 0) zero = true;
                continue;
            }
            if (zero) {
                sb.append("零");
                zero = false;
            }
            sb.append(DIGITS[d]).append(UNITS[i]);
        }
        return sb.toString();
    }
}
