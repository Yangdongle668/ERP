package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.PriceAdjustLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PriceAdjustLineMapper extends BaseMapperX<PriceAdjustLineDO> {

    default List<PriceAdjustLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<PriceAdjustLineDO>().eq(PriceAdjustLineDO::getAdjustId, parentId).orderByAsc(PriceAdjustLineDO::getLineNo));
    }

    default List<PriceAdjustLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<PriceAdjustLineDO>().in(PriceAdjustLineDO::getAdjustId, parentIds).orderByAsc(PriceAdjustLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_price_adjust_line WHERE adjust_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
