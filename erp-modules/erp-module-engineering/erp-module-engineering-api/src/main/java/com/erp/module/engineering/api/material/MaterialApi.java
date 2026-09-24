package com.erp.module.engineering.api.material;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 物料查询与校验，供其他模块调用。 */
public interface MaterialApi {

    Optional<MaterialDTO> getMaterial(Long id);

    /** 批量查询，结果顺序不保证；不存在的 ID 不会出现在结果中。 */
    List<MaterialDTO> getMaterials(Collection<Long> ids);

    /**
     * 校验物料存在且已启用（新建业务单据时调用）。
     *
     * @throws com.erp.common.exception.BizException 不存在或未启用
     */
    MaterialDTO validateUsable(Long id);
}
