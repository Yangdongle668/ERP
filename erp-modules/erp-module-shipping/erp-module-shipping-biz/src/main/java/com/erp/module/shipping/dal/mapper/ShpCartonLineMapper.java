package com.erp.module.shipping.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.shipping.dal.dataobject.ShpCartonLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ShpCartonLineMapper extends BaseMapperX<ShpCartonLineDO> {

    default List<ShpCartonLineDO> selectByNotice(Long noticeId) {
        return selectList(new LambdaQueryWrapper<ShpCartonLineDO>().eq(ShpCartonLineDO::getNoticeId, noticeId));
    }

    default List<ShpCartonLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ShpCartonLineDO>().eq(ShpCartonLineDO::getCartonId, parentId).orderByAsc(ShpCartonLineDO::getId));
    }

    default List<ShpCartonLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ShpCartonLineDO>().in(ShpCartonLineDO::getCartonId, parentIds).orderByAsc(ShpCartonLineDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM shp_carton_line WHERE carton_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
