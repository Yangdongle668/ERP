package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.SupplierContactDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SupplierContactMapper extends BaseMapperX<SupplierContactDO> {

    default List<SupplierContactDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<SupplierContactDO>().eq(SupplierContactDO::getSupplierId, parentId).orderByAsc(SupplierContactDO::getId));
    }

    default List<SupplierContactDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<SupplierContactDO>().in(SupplierContactDO::getSupplierId, parentIds).orderByAsc(SupplierContactDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_supplier_contact WHERE supplier_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
