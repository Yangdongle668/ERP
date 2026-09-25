package com.erp.module.sales.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.sales.dal.dataobject.SalForecastDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SalForecastMapper extends BaseMapperX<SalForecastDO> {

    /** 列表分页，受数据范围约束（业务员 owner_id、部门 dept_id）。自定义 SQL 不会自动加逻辑删除条件，调用方的条件中需包含 deleted = 0。 */
    @DataScope
    @Select("SELECT * FROM sal_forecast ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_SalForecastDO")
    IPage<SalForecastDO> selectScopedPage(IPage<SalForecastDO> page, @Param(Constants.WRAPPER) Wrapper<SalForecastDO> queryWrapper);

    /** 列表（导出、报表），受数据范围约束 */
    @DataScope
    @Select("SELECT * FROM sal_forecast ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_SalForecastDO")
    List<SalForecastDO> selectScopedList(@Param(Constants.WRAPPER) Wrapper<SalForecastDO> queryWrapper);
}
