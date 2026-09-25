package com.erp.module.sales.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.sales.dal.dataobject.SalOrderChangeLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SalOrderChangeLineMapper extends BaseMapperX<SalOrderChangeLineDO> {

    default List<SalOrderChangeLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<SalOrderChangeLineDO>().eq(SalOrderChangeLineDO::getChangeId, parentId).orderByAsc(SalOrderChangeLineDO::getLineNo));
    }

    default List<SalOrderChangeLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<SalOrderChangeLineDO>().in(SalOrderChangeLineDO::getChangeId, parentIds).orderByAsc(SalOrderChangeLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM sal_order_change_line WHERE change_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
