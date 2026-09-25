package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.ReturnDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReturnMapper extends BaseMapperX<ReturnDO> {

    /** 列表分页，受数据范围约束。自定义 SQL 不会自动加逻辑删除条件，调用方的条件中需包含 deleted = 0。 */
    @DataScope
    @Select("SELECT * FROM pur_return ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_ReturnDO")
    IPage<ReturnDO> selectScopedPage(IPage<ReturnDO> page, @Param(Constants.WRAPPER) Wrapper<ReturnDO> queryWrapper);

    /** 列表（导出），受数据范围约束 */
    @DataScope
    @Select("SELECT * FROM pur_return ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_ReturnDO")
    List<ReturnDO> selectScopedList(@Param(Constants.WRAPPER) Wrapper<ReturnDO> queryWrapper);
}
