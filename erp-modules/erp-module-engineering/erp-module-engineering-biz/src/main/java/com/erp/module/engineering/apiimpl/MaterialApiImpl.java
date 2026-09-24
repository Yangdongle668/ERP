package com.erp.module.engineering.apiimpl;

import com.erp.common.exception.BizException;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPlanAttr;
import com.erp.module.engineering.api.material.MaterialPurchaseAttr;
import com.erp.module.engineering.api.material.MaterialQualityAttr;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialStockAttr;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.dataobject.MaterialUomDO;
import com.erp.module.engineering.dal.mapper.MaterialMapper;
import com.erp.module.engineering.dal.mapper.MaterialUomMapper;
import com.erp.module.engineering.service.MaterialQueries;
import com.erp.module.system.api.uom.UomApi;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class MaterialApiImpl implements MaterialApi {

    private final MaterialMapper materialMapper;
    private final MaterialUomMapper uomMapper;
    private final UomApi uomApi;

    public MaterialApiImpl(MaterialMapper materialMapper, MaterialUomMapper uomMapper, UomApi uomApi) {
        this.materialMapper = materialMapper;
        this.uomMapper = uomMapper;
        this.uomApi = uomApi;
    }

    @Override
    public Optional<MaterialDTO> getMaterial(Long id) {
        return Optional.ofNullable(id == null ? null : materialMapper.selectById(id)).map(MaterialQueries::toDto);
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

    @Override
    public MaterialPlanAttr getPlanAttr(Long id) {
        MaterialDO m = get(id);
        return new MaterialPlanAttr(m.getId(), m.getSourceType(), nz(m.getLeadTimeDays()), m.getSafetyStock(), m.getMaxStock(), m.getOrderPolicy(),
                m.getFixedLotQty(), m.getPeriodDays(), m.getMoq(), m.getMpq(), m.getPlannerId(), nz(m.getLowLevelCode()));
    }

    @Override
    public MaterialPurchaseAttr getPurchaseAttr(Long id) {
        MaterialDO m = get(id);
        return new MaterialPurchaseAttr(m.getId(), m.getBuyerId(), m.getPurchaseUom() == null ? m.getBaseUom() : m.getPurchaseUom(),
                m.getOverReceivePct(), nz(m.getLeadTimeDays()), m.getMoq(), m.getMpq(), m.getPurchaseTaxRate());
    }

    @Override
    public MaterialStockAttr getStockAttr(Long id) {
        MaterialDO m = get(id);
        return new MaterialStockAttr(m.getId(), m.getBaseUom(), m.getTracking(), m.getIssueRule(), m.getShelfLifeDays(), m.getMinRemainingLifePct(),
                m.getSafetyStock(), m.getMaxStock(), m.getUnitNetWeight(), m.getUnitGrossWeight());
    }

    @Override
    public MaterialQualityAttr getQualityAttr(Long id) {
        MaterialDO m = get(id);
        return new MaterialQualityAttr(m.getId(), Boolean.TRUE.equals(m.getIqcRequired()), Boolean.TRUE.equals(m.getFqcRequired()),
                Boolean.TRUE.equals(m.getOqcRequired()));
    }

    /** 物料换算优先，其次通用换算（需求 05-02 第 2 节换算优先级） */
    @Override
    public BigDecimal convertToBase(Long materialId, BigDecimal qty, String uom) {
        MaterialDO m = get(materialId);
        if (qty == null) return null;
        String u = uom == null ? m.getBaseUom() : uom.trim().toUpperCase();
        if (u.equalsIgnoreCase(m.getBaseUom())) return uomApi.round(qty, m.getBaseUom());
        MaterialUomDO conv = uomMapper.selectOne(m.getId(), u);
        if (conv != null) return uomApi.round(qty.multiply(conv.getRate()), m.getBaseUom());
        try {
            return uomApi.convert(qty, u, m.getBaseUom());
        } catch (BizException e) {
            throw BizException.of(EngineeringErrorCodes.MATERIAL_CONVERT_FAILED, m.getCode(), u, m.getBaseUom());
        }
    }

    @Override
    public List<MaterialDTO> search(String keyword, Collection<MaterialType> types, int limit) {
        return materialMapper.search(keyword, types, MaterialStatus.ENABLED, limit).stream().map(MaterialQueries::toDto).toList();
    }

    private MaterialDO get(Long id) {
        MaterialDO m = id == null ? null : materialMapper.selectById(id);
        if (m == null) throw new BizException(EngineeringErrorCodes.MATERIAL_NOT_EXISTS);
        return m;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
