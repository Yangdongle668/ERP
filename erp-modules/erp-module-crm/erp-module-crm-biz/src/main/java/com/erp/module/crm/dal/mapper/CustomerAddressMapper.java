package com.erp.module.crm.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.crm.dal.dataobject.CustomerAddressDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CustomerAddressMapper extends BaseMapperX<CustomerAddressDO> {

    default List<CustomerAddressDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<CustomerAddressDO>().eq(CustomerAddressDO::getCustomerId, parentId).orderByAsc(CustomerAddressDO::getId));
    }

    default List<CustomerAddressDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<CustomerAddressDO>().in(CustomerAddressDO::getCustomerId, parentIds).orderByAsc(CustomerAddressDO::getId));
    }

    /** 物理删除（子表随主表保存整体替换） */
    @Delete("DELETE FROM crm_address WHERE customer_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
