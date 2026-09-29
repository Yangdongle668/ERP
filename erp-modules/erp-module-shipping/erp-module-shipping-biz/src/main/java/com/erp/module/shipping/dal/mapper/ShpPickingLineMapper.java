package com.erp.module.shipping.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.shipping.dal.dataobject.ShpPickingLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ShpPickingLineMapper extends BaseMapperX<ShpPickingLineDO> {

    default List<ShpPickingLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ShpPickingLineDO>().eq(ShpPickingLineDO::getPickingId, parentId).orderByAsc(ShpPickingLineDO::getLineNo));
    }

    default List<ShpPickingLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ShpPickingLineDO>().in(ShpPickingLineDO::getPickingId, parentIds).orderByAsc(ShpPickingLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM shp_picking_line WHERE picking_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
