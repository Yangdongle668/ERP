package com.erp.module.system.api.uom;

/** 计量单位 */
public record UomDTO(String code, String name, String nameEn, String category, int precision, boolean enabled) {
}
