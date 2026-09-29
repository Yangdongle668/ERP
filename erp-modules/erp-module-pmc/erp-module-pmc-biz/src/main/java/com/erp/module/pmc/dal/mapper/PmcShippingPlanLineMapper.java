package com.erp.module.pmc.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.pmc.dal.dataobject.PmcShippingPlanLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PmcShippingPlanLineMapper extends BaseMapperX<PmcShippingPlanLineDO> {

    default List<PmcShippingPlanLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<PmcShippingPlanLineDO>().eq(PmcShippingPlanLineDO::getPlanId, parentId).orderByAsc(PmcShippingPlanLineDO::getLineNo));
    }

    default List<PmcShippingPlanLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<PmcShippingPlanLineDO>().in(PmcShippingPlanLineDO::getPlanId, parentIds).orderByAsc(PmcShippingPlanLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pmc_shipping_plan_line WHERE plan_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
