package com.erp.module.finance.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.finance.dal.dataobject.FinPayableLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface FinPayableLineMapper extends BaseMapperX<FinPayableLineDO> {

    default List<FinPayableLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<FinPayableLineDO>().eq(FinPayableLineDO::getPayableId, parentId).orderByAsc(FinPayableLineDO::getLineNo));
    }

    default List<FinPayableLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<FinPayableLineDO>().in(FinPayableLineDO::getPayableId, parentIds).orderByAsc(FinPayableLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM fin_payable_line WHERE payable_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
