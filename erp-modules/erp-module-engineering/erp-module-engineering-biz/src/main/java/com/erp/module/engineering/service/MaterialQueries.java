package com.erp.module.engineering.service;

import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;

/** 供本模块其他包（如 apiimpl）使用的转换入口。 */
public final class MaterialQueries {

    private MaterialQueries() {
    }

    public static MaterialDTO toDto(MaterialDO material) {
        return MaterialConvert.toDto(material);
    }
}
