package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.SupplierCertDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SupplierCertMapper extends BaseMapperX<SupplierCertDO> {

    default List<SupplierCertDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<SupplierCertDO>().eq(SupplierCertDO::getSupplierId, parentId).orderByAsc(SupplierCertDO::getId));
    }

    default List<SupplierCertDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<SupplierCertDO>().in(SupplierCertDO::getSupplierId, parentIds).orderByAsc(SupplierCertDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_supplier_cert WHERE supplier_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
