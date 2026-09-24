package com.erp.module.system.api.currency;

/** 币别 */
public record CurrencyDTO(String code, String name, String nameEn, String symbol, int amountPrecision, boolean base, boolean enabled) {
}
