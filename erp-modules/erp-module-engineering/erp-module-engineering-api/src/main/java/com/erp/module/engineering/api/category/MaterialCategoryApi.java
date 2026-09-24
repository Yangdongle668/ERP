package com.erp.module.engineering.api.category;

import java.util.List;
import java.util.Optional;

/** 物料类别（仓库按类别确定默认仓、BI 按类别分组）。 */
public interface MaterialCategoryApi {

    Optional<MaterialCategoryDTO> get(Long id);

    /** 该类别及其全部下级类别的 ID（含自身）；类别不存在时为空列表 */
    List<Long> getDescendantIds(Long id);

    /** 全部类别（含停用），按层级、排序 */
    List<MaterialCategoryDTO> listAll();
}
