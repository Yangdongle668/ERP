package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.ProjectTaskDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProjectTaskMapper extends BaseMapperX<ProjectTaskDO> {

    default List<ProjectTaskDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ProjectTaskDO>().eq(ProjectTaskDO::getProjectId, parentId).orderByAsc(ProjectTaskDO::getPlanStart));
    }

    default List<ProjectTaskDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ProjectTaskDO>().in(ProjectTaskDO::getProjectId, parentIds).orderByAsc(ProjectTaskDO::getPlanStart));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM eng_project_task WHERE project_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
