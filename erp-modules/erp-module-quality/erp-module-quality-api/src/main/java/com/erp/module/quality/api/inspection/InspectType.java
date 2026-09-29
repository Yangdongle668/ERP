package com.erp.module.quality.api.inspection;

/** 检验类型：来料 / 制程 / 成品 / 出货 / 退货 / 复检（需求 10-品质 README 第 2 节） */
public enum InspectType {
    IQC("来料检验"), IPQC("制程检验"), FQC("成品检验"), OQC("出货检验"), RETURN("退货检验"), RECHECK("复检");

    private final String label;

    InspectType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 判定后是否需要库存调拨（被检物在待检仓 / 退货仓） */
    public boolean stockAction() {
        return this == IQC || this == FQC || this == RETURN || this == RECHECK;
    }
}
