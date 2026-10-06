package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.CodeSegmentValueDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CodeSegmentValueMapper extends BaseMapperX<CodeSegmentValueDO> {

    default List<CodeSegmentValueDO> selectBySegments(Collection<Long> segmentIds) {
        if (segmentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<CodeSegmentValueDO>().in(CodeSegmentValueDO::getSegmentId, segmentIds)
                .orderByAsc(CodeSegmentValueDO::getSort).orderByAsc(CodeSegmentValueDO::getValueCode));
    }

    @org.apache.ibatis.annotations.Delete("DELETE FROM eng_code_segment_value WHERE segment_id IN (SELECT id FROM eng_code_segment WHERE category_id = #{categoryId})")
    int hardDeleteByCategory(@org.apache.ibatis.annotations.Param("categoryId") Long categoryId);
}
