package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.CodeSegmentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CodeSegmentMapper extends BaseMapperX<CodeSegmentDO> {

    default List<CodeSegmentDO> selectByCategory(Long categoryId) {
        return selectList(new LambdaQueryWrapper<CodeSegmentDO>().eq(CodeSegmentDO::getCategoryId, categoryId)
                .orderByAsc(CodeSegmentDO::getSort).orderByAsc(CodeSegmentDO::getId));
    }

    default List<CodeSegmentDO> selectByCategories(Collection<Long> categoryIds) {
        if (categoryIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<CodeSegmentDO>().in(CodeSegmentDO::getCategoryId, categoryIds));
    }
}
