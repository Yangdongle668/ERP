package com.erp.module.sales.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.sales.dal.dataobject.SalRfqLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SalRfqLineMapper extends BaseMapperX<SalRfqLineDO> {

    default List<SalRfqLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<SalRfqLineDO>().eq(SalRfqLineDO::getRfqId, parentId).orderByAsc(SalRfqLineDO::getLineNo));
    }

    default List<SalRfqLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<SalRfqLineDO>().in(SalRfqLineDO::getRfqId, parentIds).orderByAsc(SalRfqLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM sal_rfq_line WHERE rfq_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
