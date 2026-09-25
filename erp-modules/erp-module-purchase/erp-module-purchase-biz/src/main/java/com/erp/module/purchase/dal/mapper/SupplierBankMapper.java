package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.SupplierBankDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SupplierBankMapper extends BaseMapperX<SupplierBankDO> {

    default List<SupplierBankDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<SupplierBankDO>().eq(SupplierBankDO::getSupplierId, parentId).orderByAsc(SupplierBankDO::getId));
    }

    default List<SupplierBankDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<SupplierBankDO>().in(SupplierBankDO::getSupplierId, parentIds).orderByAsc(SupplierBankDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_supplier_bank WHERE supplier_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
