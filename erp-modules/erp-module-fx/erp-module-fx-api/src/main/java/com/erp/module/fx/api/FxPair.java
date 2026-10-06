package com.erp.module.fx.api;

/**
 * 汇率对：1 单位外币 = rate 人民币（中国银行现汇买入价，页面每 100 外币，换算为每 1 外币）。
 * 只有美元保存历史（报价、日平均、月平均）并写入系统汇率表；其他币别只提供实时报价（15 分钟缓存，不入库）。
 */
public enum FxPair {
    USD_CNY("USD", "美元 / 人民币", true),
    EUR_CNY("EUR", "欧元 / 人民币", false),
    JPY_CNY("JPY", "日元 / 人民币", false),
    KRW_CNY("KRW", "韩元 / 人民币", false),
    AUD_CNY("AUD", "澳元 / 人民币", false);

    private final String from;
    private final String label;
    private final boolean persisted;

    FxPair(String from, String label, boolean persisted) {
        this.from = from;
        this.label = label;
        this.persisted = persisted;
    }

    /** 是否保存历史、计算日 / 月平均并推送系统汇率表 */
    public boolean persisted() {
        return persisted;
    }

    public String from() {
        return from;
    }

    public String to() {
        return "CNY";
    }

    public String label() {
        return label;
    }
}
