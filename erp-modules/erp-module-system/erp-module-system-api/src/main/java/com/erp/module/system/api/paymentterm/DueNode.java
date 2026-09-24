package com.erp.module.system.api.paymentterm;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 到期计算结果：节点金额与到期日（事件尚未发生时 dueDate 为空） */
public record DueNode(int seq, String name, BigDecimal percent, BigDecimal amount, BaseEvent baseEvent, int days, LocalDate dueDate) {
}
