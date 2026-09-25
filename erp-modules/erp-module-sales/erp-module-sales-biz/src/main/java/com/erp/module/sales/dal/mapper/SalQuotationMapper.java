package com.erp.module.sales.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.sales.dal.dataobject.SalQuotationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SalQuotationMapper extends BaseMapperX<SalQuotationDO> {

    /** 列表分页，受数据范围约束（业务员 owner_id、部门 dept_id）。自定义 SQL 不会自动加逻辑删除条件，调用方的条件中需包含 deleted = 0。 */
    @DataScope
    @Select("SELECT * FROM sal_quotation ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_SalQuotationDO")
    IPage<SalQuotationDO> selectScopedPage(IPage<SalQuotationDO> page, @Param(Constants.WRAPPER) Wrapper<SalQuotationDO> queryWrapper);

    /** 列表（导出、报表），受数据范围约束 */
    @DataScope
    @Select("SELECT * FROM sal_quotation ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_SalQuotationDO")
    List<SalQuotationDO> selectScopedList(@Param(Constants.WRAPPER) Wrapper<SalQuotationDO> queryWrapper);
}
