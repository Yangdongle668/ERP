package com.erp.module.crm.api.credit;

import java.math.BigDecimal;

/**
 * 信用检查结果（需求 03-03 R01～R03）。
 *
 * @param pass    false 时调用方阻止；mode 为 WARN 且有 message 时调用方显示警告，由用户确认继续
 * @param mode    NONE / WARN / BLOCK（已按客户设置与系统参数确定）
 * @param limit   信用额度，为空表示未设置额度（只检查逾期）
 * @param used    已用额度（本位币）
 * @param available 可用额度，未设置额度时为空
 * @param message 超额或逾期时的提示，正常时为空
 */
public record CreditCheckResult(boolean pass, String mode, BigDecimal limit, BigDecimal used, BigDecimal available, BigDecimal overdue,
                                String message) {
}
