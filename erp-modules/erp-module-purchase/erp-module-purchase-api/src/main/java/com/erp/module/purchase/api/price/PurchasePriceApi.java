package com.erp.module.purchase.api.price;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** 采购价格查询（销售报价核算、财务使用）。价格为每基本单位。 */
public interface PurchasePriceApi {

    /**
     * 取价（PUR-PRC-R04）：该日期有效、币别相同、阶梯起始数量 ≤ 数量的最大一档；没有返回空。
     *
     * @param qty 基本单位数量
     */
    Optional<PurchasePriceDTO> getEffectivePrice(Long supplierId, Long materialId, BigDecimal qty, LocalDate date, String currency);

    /** 物料最近生效的采购价格（任意供应商、任意币别，第一档） */
    Optional<PurchasePriceDTO> getLatestPrice(Long materialId);
}
