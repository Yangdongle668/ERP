package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.SupplierMaterialDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SupplierMaterialMapper extends BaseMapperX<SupplierMaterialDO> {

    default List<SupplierMaterialDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<SupplierMaterialDO>().eq(SupplierMaterialDO::getSupplierId, parentId).orderByAsc(SupplierMaterialDO::getId));
    }

    default List<SupplierMaterialDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<SupplierMaterialDO>().in(SupplierMaterialDO::getSupplierId, parentIds).orderByAsc(SupplierMaterialDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_supplier_material WHERE supplier_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
