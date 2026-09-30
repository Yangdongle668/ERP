package com.erp.module.finance.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.finance.dal.dataobject.FinCostOrderMaterialDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface FinCostOrderMaterialMapper extends BaseMapperX<FinCostOrderMaterialDO> {

    default List<FinCostOrderMaterialDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<FinCostOrderMaterialDO>().eq(FinCostOrderMaterialDO::getCostOrderId, parentId).orderByAsc(FinCostOrderMaterialDO::getMaterialId));
    }

    default List<FinCostOrderMaterialDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<FinCostOrderMaterialDO>().in(FinCostOrderMaterialDO::getCostOrderId, parentIds).orderByAsc(FinCostOrderMaterialDO::getMaterialId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM fin_cost_order_material WHERE cost_order_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);

    /** 重新计算前清除本期结果（物理删除，避免与唯一键冲突） */
    @Delete("DELETE FROM fin_cost_order_material WHERE period = #{period}")
    int deleteByPeriod(@Param("period") String period);
}
