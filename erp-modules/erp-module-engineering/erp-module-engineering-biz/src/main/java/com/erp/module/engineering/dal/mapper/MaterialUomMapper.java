package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.MaterialUomDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MaterialUomMapper extends BaseMapperX<MaterialUomDO> {

    default List<MaterialUomDO> selectByMaterial(Long materialId) {
        return selectList(new LambdaQueryWrapper<MaterialUomDO>().eq(MaterialUomDO::getMaterialId, materialId).orderByAsc(MaterialUomDO::getId));
    }

    default List<MaterialUomDO> selectByMaterials(Collection<Long> materialIds) {
        if (materialIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<MaterialUomDO>().in(MaterialUomDO::getMaterialId, materialIds));
    }

    default MaterialUomDO selectOne(Long materialId, String uom) {
        return selectOne(new LambdaQueryWrapper<MaterialUomDO>().eq(MaterialUomDO::getMaterialId, materialId).eq(MaterialUomDO::getUom, uom));
    }

    default long countByUom(String uom) {
        return selectCount(new LambdaQueryWrapper<MaterialUomDO>().eq(MaterialUomDO::getUom, uom));
    }

    /** 物理删除（换算行随物料保存整体替换，不保留逻辑删除记录） */
    @Delete("DELETE FROM eng_material_uom WHERE material_id = #{materialId}")
    int deleteByMaterial(@Param("materialId") Long materialId);
}
