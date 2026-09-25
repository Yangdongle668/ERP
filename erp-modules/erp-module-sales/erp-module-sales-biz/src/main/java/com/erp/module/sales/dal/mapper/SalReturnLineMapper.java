package com.erp.module.sales.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.sales.dal.dataobject.SalReturnLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SalReturnLineMapper extends BaseMapperX<SalReturnLineDO> {

    default List<SalReturnLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<SalReturnLineDO>().eq(SalReturnLineDO::getReturnId, parentId).orderByAsc(SalReturnLineDO::getLineNo));
    }

    default List<SalReturnLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<SalReturnLineDO>().in(SalReturnLineDO::getReturnId, parentIds).orderByAsc(SalReturnLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM sal_return_line WHERE return_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
