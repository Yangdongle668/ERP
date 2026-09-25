package com.erp.module.crm.api.credit;

import java.math.BigDecimal;

/**
 * 信用占用的组成部分（本位币）。提供方只填写自己负责的项，其他项为空：
 * 财务填写应收余额（未核销应收 − 未核销预收）和逾期应收；销售填写未出货订单金额（含税）。
 */
public record CreditUsage(BigDecimal receivableBalance, BigDecimal overdueAmount, BigDecimal openOrderAmount) {
}
