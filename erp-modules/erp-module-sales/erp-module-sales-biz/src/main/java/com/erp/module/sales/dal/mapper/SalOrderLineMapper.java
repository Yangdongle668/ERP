package com.erp.module.sales.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SalOrderLineMapper extends BaseMapperX<SalOrderLineDO> {

    default List<SalOrderLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<SalOrderLineDO>().eq(SalOrderLineDO::getOrderId, parentId).orderByAsc(SalOrderLineDO::getLineNo));
    }

    default List<SalOrderLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<SalOrderLineDO>().in(SalOrderLineDO::getOrderId, parentIds).orderByAsc(SalOrderLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM sal_order_line WHERE order_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
