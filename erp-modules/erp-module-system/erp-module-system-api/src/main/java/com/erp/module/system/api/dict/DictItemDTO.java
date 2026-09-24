package com.erp.module.system.api.dict;

/** 字典项 */
public record DictItemDTO(String value, String label, String labelEn, String tagType, int sort, boolean isDefault, boolean enabled) {
}
