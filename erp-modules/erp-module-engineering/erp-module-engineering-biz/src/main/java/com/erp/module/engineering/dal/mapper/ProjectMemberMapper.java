package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.ProjectMemberDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProjectMemberMapper extends BaseMapperX<ProjectMemberDO> {

    default List<ProjectMemberDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ProjectMemberDO>().eq(ProjectMemberDO::getProjectId, parentId).orderByAsc(ProjectMemberDO::getId));
    }

    default List<ProjectMemberDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ProjectMemberDO>().in(ProjectMemberDO::getProjectId, parentIds).orderByAsc(ProjectMemberDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM eng_project_member WHERE project_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
