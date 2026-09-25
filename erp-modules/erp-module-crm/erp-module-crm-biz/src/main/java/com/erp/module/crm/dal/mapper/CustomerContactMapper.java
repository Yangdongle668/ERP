package com.erp.module.crm.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.crm.dal.dataobject.CustomerContactDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CustomerContactMapper extends BaseMapperX<CustomerContactDO> {

    default List<CustomerContactDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<CustomerContactDO>().eq(CustomerContactDO::getCustomerId, parentId).orderByAsc(CustomerContactDO::getId));
    }

    default List<CustomerContactDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<CustomerContactDO>().in(CustomerContactDO::getCustomerId, parentIds).orderByAsc(CustomerContactDO::getId));
    }

    /** 物理删除（子表随主表保存整体替换） */
    @Delete("DELETE FROM crm_contact WHERE customer_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
