package com.erp.module.sales.api.price;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** 销售取价（出货补货订单、BI）。 */
public interface SalesPriceApi {

    /** 按基本单位取价（qty 为基本单位数量） */
    Optional<SalesPriceDTO> getPrice(Long customerId, Long materialId, BigDecimal qty, LocalDate date, String currency);

    /**
     * 按指定销售单位取价：客户价格表 → 等级价格表 → 通用价格表 → 有效报价 → 最近成交价；都没有返回空。
     *
     * @param qty uom 单位的数量（用于匹配阶梯）
     */
    Optional<SalesPriceDTO> getPrice(Long customerId, Long materialId, BigDecimal qty, String uom, LocalDate date, String currency);
}
