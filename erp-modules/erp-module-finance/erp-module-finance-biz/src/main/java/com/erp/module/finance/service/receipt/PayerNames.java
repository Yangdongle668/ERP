package com.erp.module.finance.service.receipt;

import java.util.Locale;
import java.util.regex.Pattern;

/** 银行流水付款方名称的规范化，用于与客户名称模糊匹配 */
public final class PayerNames {

    private static final Pattern SUFFIX = Pattern.compile(
            "(有限责任公司|股份有限公司|有限公司|集团|公司|co\\.?,?\\s*ltd\\.?|company\\s+limited|limited|ltd\\.?|incorporated|inc\\.?|corporation|corp\\.?|llc|gmbh|s\\.?a\\.?|b\\.?v\\.?)+$");
    private static final Pattern NOISE = Pattern.compile("[\\s\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFF0F（）()、，。·]+");

    private PayerNames() {
    }

    /** 转小写，去掉标点空白与公司后缀（有限公司 / Co., Ltd / Inc 等） */
    public static String normalize(String name) {
        if (name == null) return "";
        String s = name.toLowerCase(Locale.ROOT).trim();
        // 先去后缀（后缀里含标点和空格），再去干扰字符
        String prev;
        do {
            prev = s;
            s = SUFFIX.matcher(s.replaceAll("[\\s,，.]+$", "")).replaceAll("");
        } while (!s.equals(prev));
        return NOISE.matcher(s).replaceAll("");
    }

    /** 两个名称规范化后相等，或一个完整包含另一个（较短者至少 4 个字符，避免“上海”“abc”之类误配） */
    public static boolean similar(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y)) return true;
        String shorter = x.length() <= y.length() ? x : y;
        String longer = shorter == x ? y : x;
        return shorter.length() >= 4 && longer.contains(shorter);
    }
}
