package com.erp.module.sales.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.sales.dal.dataobject.SalForecastLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SalForecastLineMapper extends BaseMapperX<SalForecastLineDO> {

    default List<SalForecastLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<SalForecastLineDO>().eq(SalForecastLineDO::getForecastId, parentId).orderByAsc(SalForecastLineDO::getId));
    }

    default List<SalForecastLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<SalForecastLineDO>().in(SalForecastLineDO::getForecastId, parentIds).orderByAsc(SalForecastLineDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM sal_forecast_line WHERE forecast_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
