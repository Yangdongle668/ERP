package com.erp.module.engineering.service;

import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;

/** DO → DTO 转换。显式手写，字段变化时编译器能发现遗漏。 */
final class MaterialConvert {

    private MaterialConvert() {
    }

    static MaterialDTO toDto(MaterialDO d) {
        return new MaterialDTO(d.getId(), d.getCode(), d.getName(), d.getSpec(), d.getMaterialType(),
                d.getCategoryId(), d.getBaseUom(), d.getStatus(), d.getNameEn(), d.getSourceType(), d.getTracking(), d.getHsCode());
    }
}
