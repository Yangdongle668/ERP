package com.erp.module.fx.api;

/** 汇率对：1 单位前一币别 = rate 单位后一币别（中国银行现汇买入价；EUR_USD 为 EUR_CNY ÷ USD_CNY 交叉汇率） */
public enum FxPair {
    USD_CNY("USD", "CNY", "美元 / 人民币"),
    EUR_CNY("EUR", "CNY", "欧元 / 人民币"),
    EUR_USD("EUR", "USD", "欧元 / 美元");

    private final String from;
    private final String to;
    private final String label;

    FxPair(String from, String to, String label) {
        this.from = from;
        this.to = to;
        this.label = label;
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    public String label() {
        return label;
    }
}
