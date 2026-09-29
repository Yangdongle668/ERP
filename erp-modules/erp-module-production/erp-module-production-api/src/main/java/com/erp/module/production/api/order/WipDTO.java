package com.erp.module.production.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 物料的在制数量：已下达（含生产中、暂停）未完工订单的剩余数量 = 计划 − 合格入库 − 报废（不小于 0）。
 */
public record WipDTO(Long materialId, BigDecimal wipQty, List<Order> orders) {

    public record Order(Long prodOrderId, String prodOrderNo, BigDecimal remainingQty, LocalDate planEnd, String prodStatus) {
    }
}
