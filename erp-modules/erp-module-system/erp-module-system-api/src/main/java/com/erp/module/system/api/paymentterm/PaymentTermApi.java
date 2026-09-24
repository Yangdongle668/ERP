package com.erp.module.system.api.paymentterm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 付款条件与到期日计算（01-14，全系统统一）。 */
public interface PaymentTermApi {

    Optional<PaymentTermDTO> get(Long id);

    /** 校验付款条件存在、启用且适用于该用途（SALES / PURCHASE） */
    PaymentTermDTO validate(Long id, String usage);

    /**
     * 计算各节点金额与到期日：节点金额 = ROUND(金额 × 比例, 2)，最后一个节点 = 金额 − 前面之和；
     * 到期日 = 事件日期 + 天数；MONTH_END = 事件所在月最后一天 + 天数（事件日期取 events 中 SHIPMENT，
     * 没有时取 RECEIPT_DATE）；事件日期缺失时到期日为空。
     *
     * @param events 已发生事件的日期，如 {ORDER_DATE: 2026-09-01, SHIPMENT: 2026-09-15}
     */
    List<DueNode> calcDueDates(Long termId, BigDecimal amount, Map<BaseEvent, LocalDate> events);
}
