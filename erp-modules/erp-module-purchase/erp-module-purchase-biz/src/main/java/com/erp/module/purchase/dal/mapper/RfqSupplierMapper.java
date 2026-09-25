package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.RfqSupplierDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RfqSupplierMapper extends BaseMapperX<RfqSupplierDO> {

    default List<RfqSupplierDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<RfqSupplierDO>().eq(RfqSupplierDO::getRfqId, parentId).orderByAsc(RfqSupplierDO::getId));
    }

    default List<RfqSupplierDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<RfqSupplierDO>().in(RfqSupplierDO::getRfqId, parentIds).orderByAsc(RfqSupplierDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_rfq_supplier WHERE rfq_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
