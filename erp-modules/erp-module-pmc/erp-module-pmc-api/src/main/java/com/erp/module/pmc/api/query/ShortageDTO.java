package com.erp.module.pmc.api.query;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产订单某子件的缺料（基本单位）。
 *
 * @param etaDate    在途覆盖缺料的预计齐套日期；在途不足时为空
 * @param noSupplyQty 在途也覆盖不了的数量（需要采购）
 */
public record ShortageDTO(Long prodOrderId, Long componentId, BigDecimal unissuedQty, BigDecimal allocatedQty, BigDecimal shortageQty,
                          LocalDate etaDate, BigDecimal noSupplyQty) {
}
