package com.erp.module.quality.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.quality.dal.dataobject.QcStandardItemDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface QcStandardItemMapper extends BaseMapperX<QcStandardItemDO> {

    default List<QcStandardItemDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<QcStandardItemDO>().eq(QcStandardItemDO::getStandardId, parentId).orderByAsc(QcStandardItemDO::getSeq));
    }

    default List<QcStandardItemDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<QcStandardItemDO>().in(QcStandardItemDO::getStandardId, parentIds).orderByAsc(QcStandardItemDO::getSeq));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM qc_standard_item WHERE standard_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
