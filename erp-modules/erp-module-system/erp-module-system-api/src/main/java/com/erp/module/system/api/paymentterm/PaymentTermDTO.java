package com.erp.module.system.api.paymentterm;

import java.math.BigDecimal;
import java.util.List;

/** 付款条件（单据保存时应保存其快照） */
public record PaymentTermDTO(Long id, String code, String name, String nameEn, String settlementMethod, String usage,
                             boolean enabled, List<Node> nodes) {

    /** @param percent 比例（小数，0.3 表示 30%） */
    public record Node(int seq, String name, BigDecimal percent, BaseEvent baseEvent, int days) {
    }
}
