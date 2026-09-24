package com.erp.module.engineering.service;

import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.controller.vo.MaterialRespVO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;

/** DO ↔ VO/DTO 转换。显式手写，字段变化时编译器能发现遗漏。 */
final class MaterialConvert {

    private MaterialConvert() {
    }

    static MaterialRespVO toResp(MaterialDO d) {
        return new MaterialRespVO(d.getId(), d.getCode(), d.getName(), d.getNameEn(), d.getSpec(),
                d.getMaterialType(), d.getCategoryId(), d.getBaseUom(), d.getStatus(), d.getRemark(),
                d.getVersion(), d.getCreatedAt(), d.getUpdatedAt());
    }

    static MaterialDTO toDto(MaterialDO d) {
        return new MaterialDTO(d.getId(), d.getCode(), d.getName(), d.getSpec(), d.getMaterialType(),
                d.getCategoryId(), d.getBaseUom(), d.getStatus());
    }
}
