package com.erp.module.shipping.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.shipping.dal.dataobject.ShpCustomsItemDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ShpCustomsItemMapper extends BaseMapperX<ShpCustomsItemDO> {

    default List<ShpCustomsItemDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ShpCustomsItemDO>().eq(ShpCustomsItemDO::getCustomsId, parentId).orderByAsc(ShpCustomsItemDO::getSeq));
    }

    default List<ShpCustomsItemDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ShpCustomsItemDO>().in(ShpCustomsItemDO::getCustomsId, parentIds).orderByAsc(ShpCustomsItemDO::getSeq));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM shp_customs_item WHERE customs_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
