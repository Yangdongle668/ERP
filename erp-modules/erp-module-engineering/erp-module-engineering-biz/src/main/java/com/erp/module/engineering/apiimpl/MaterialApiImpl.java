package com.erp.module.engineering.apiimpl;

import com.erp.common.exception.BizException;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.dal.mapper.MaterialMapper;
import com.erp.module.engineering.service.MaterialQueries;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class MaterialApiImpl implements MaterialApi {

    private final MaterialMapper materialMapper;

    public MaterialApiImpl(MaterialMapper materialMapper) {
        this.materialMapper = materialMapper;
    }

    @Override
    public Optional<MaterialDTO> getMaterial(Long id) {
        return Optional.ofNullable(materialMapper.selectById(id)).map(MaterialQueries::toDto);
    }

    @Override
    public List<MaterialDTO> getMaterials(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return materialMapper.selectBatchIds(ids).stream().map(MaterialQueries::toDto).toList();
    }

    @Override
    public MaterialDTO validateUsable(Long id) {
        MaterialDTO material = getMaterial(id)
                .orElseThrow(() -> new BizException(EngineeringErrorCodes.MATERIAL_NOT_EXISTS));
        if (material.status() != MaterialStatus.ENABLED) {
            throw BizException.of(EngineeringErrorCodes.MATERIAL_NOT_ENABLED, material.code());
        }
        return material;
    }
}
