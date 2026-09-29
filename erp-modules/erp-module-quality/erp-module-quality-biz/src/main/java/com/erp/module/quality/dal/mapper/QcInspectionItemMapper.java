package com.erp.module.quality.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.quality.dal.dataobject.QcInspectionItemDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface QcInspectionItemMapper extends BaseMapperX<QcInspectionItemDO> {

    default List<QcInspectionItemDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<QcInspectionItemDO>().eq(QcInspectionItemDO::getInspectionId, parentId).orderByAsc(QcInspectionItemDO::getSeq));
    }

    default List<QcInspectionItemDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<QcInspectionItemDO>().in(QcInspectionItemDO::getInspectionId, parentIds).orderByAsc(QcInspectionItemDO::getSeq));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM qc_inspection_item WHERE inspection_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
