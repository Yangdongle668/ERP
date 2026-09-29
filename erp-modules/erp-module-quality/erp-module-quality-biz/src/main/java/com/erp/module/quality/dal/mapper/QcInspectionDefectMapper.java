package com.erp.module.quality.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.quality.dal.dataobject.QcInspectionDefectDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface QcInspectionDefectMapper extends BaseMapperX<QcInspectionDefectDO> {

    default List<QcInspectionDefectDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<QcInspectionDefectDO>().eq(QcInspectionDefectDO::getInspectionId, parentId).orderByAsc(QcInspectionDefectDO::getId));
    }

    default List<QcInspectionDefectDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<QcInspectionDefectDO>().in(QcInspectionDefectDO::getInspectionId, parentIds).orderByAsc(QcInspectionDefectDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM qc_inspection_defect WHERE inspection_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
