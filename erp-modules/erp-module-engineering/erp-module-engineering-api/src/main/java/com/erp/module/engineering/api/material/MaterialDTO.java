package com.erp.module.engineering.api.material;

/** 物料对外视图（跨模块使用）。只包含其他模块需要的字段，新增字段不影响调用方。 */
public record MaterialDTO(
        Long id,
        String code,
        String name,
        String spec,
        MaterialType materialType,
        Long categoryId,
        String baseUom,
        MaterialStatus status) {
}
