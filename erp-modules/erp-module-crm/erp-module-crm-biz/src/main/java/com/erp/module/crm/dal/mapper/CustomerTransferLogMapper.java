package com.erp.module.crm.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.crm.dal.dataobject.CustomerTransferLogDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CustomerTransferLogMapper extends BaseMapperX<CustomerTransferLogDO> {

    default List<CustomerTransferLogDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<CustomerTransferLogDO>().eq(CustomerTransferLogDO::getCustomerId, parentId).orderByAsc(CustomerTransferLogDO::getId));
    }

    default List<CustomerTransferLogDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<CustomerTransferLogDO>().in(CustomerTransferLogDO::getCustomerId, parentIds).orderByAsc(CustomerTransferLogDO::getId));
    }

    /** 物理删除（子表随主表保存整体替换） */
    @Delete("DELETE FROM crm_customer_transfer_log WHERE customer_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
