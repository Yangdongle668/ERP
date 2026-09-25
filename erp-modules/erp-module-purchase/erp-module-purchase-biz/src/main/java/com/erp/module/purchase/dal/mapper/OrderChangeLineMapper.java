package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.OrderChangeLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface OrderChangeLineMapper extends BaseMapperX<OrderChangeLineDO> {

    default List<OrderChangeLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<OrderChangeLineDO>().eq(OrderChangeLineDO::getChangeId, parentId).orderByAsc(OrderChangeLineDO::getLineNo));
    }

    default List<OrderChangeLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<OrderChangeLineDO>().in(OrderChangeLineDO::getChangeId, parentIds).orderByAsc(OrderChangeLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_order_change_line WHERE change_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
