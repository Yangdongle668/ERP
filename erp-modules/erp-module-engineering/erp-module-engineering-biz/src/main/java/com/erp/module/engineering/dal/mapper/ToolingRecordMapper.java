package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.ToolingRecordDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ToolingRecordMapper extends BaseMapperX<ToolingRecordDO> {

    default List<ToolingRecordDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ToolingRecordDO>().eq(ToolingRecordDO::getToolingId, parentId).orderByAsc(ToolingRecordDO::getId));
    }

    default List<ToolingRecordDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ToolingRecordDO>().in(ToolingRecordDO::getToolingId, parentIds).orderByAsc(ToolingRecordDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM eng_tooling_record WHERE tooling_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
