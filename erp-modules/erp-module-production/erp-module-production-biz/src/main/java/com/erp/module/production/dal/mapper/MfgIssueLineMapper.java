package com.erp.module.production.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.production.dal.dataobject.MfgIssueLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MfgIssueLineMapper extends BaseMapperX<MfgIssueLineDO> {

    default List<MfgIssueLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<MfgIssueLineDO>().eq(MfgIssueLineDO::getIssueId, parentId).orderByAsc(MfgIssueLineDO::getLineNo));
    }

    default List<MfgIssueLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<MfgIssueLineDO>().in(MfgIssueLineDO::getIssueId, parentIds).orderByAsc(MfgIssueLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM mfg_issue_line WHERE issue_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
