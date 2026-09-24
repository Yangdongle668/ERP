package com.erp.module.engineering.api.category;

import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.Tracking;

/** 物料类别对外视图 */
public record MaterialCategoryDTO(Long id, Long parentId, String code, String name, String codePrefix, MaterialType defaultMaterialType,
                                  String defaultBaseUom, Tracking defaultTracking, boolean defaultIqcRequired, Integer defaultShelfLifeDays,
                                  int level, boolean enabled) {
}
