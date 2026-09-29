package com.erp.module.finance.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.finance.dal.dataobject.FinReceivableLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface FinReceivableLineMapper extends BaseMapperX<FinReceivableLineDO> {

    default List<FinReceivableLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<FinReceivableLineDO>().eq(FinReceivableLineDO::getReceivableId, parentId).orderByAsc(FinReceivableLineDO::getLineNo));
    }

    default List<FinReceivableLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<FinReceivableLineDO>().in(FinReceivableLineDO::getReceivableId, parentIds).orderByAsc(FinReceivableLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM fin_receivable_line WHERE receivable_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
