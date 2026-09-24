package com.erp.module.engineering.api.material;

/** 质量属性（品质使用）：来料、完工、出货是否检验 */
public record MaterialQualityAttr(Long materialId, boolean iqcRequired, boolean fqcRequired, boolean oqcRequired) {
}
