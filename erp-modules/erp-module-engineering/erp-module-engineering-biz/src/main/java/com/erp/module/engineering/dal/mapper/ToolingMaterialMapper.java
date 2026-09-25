package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.ToolingMaterialDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ToolingMaterialMapper extends BaseMapperX<ToolingMaterialDO> {

    default List<ToolingMaterialDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ToolingMaterialDO>().eq(ToolingMaterialDO::getToolingId, parentId).orderByAsc(ToolingMaterialDO::getId));
    }

    default List<ToolingMaterialDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ToolingMaterialDO>().in(ToolingMaterialDO::getToolingId, parentIds).orderByAsc(ToolingMaterialDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM eng_tooling_material WHERE tooling_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
