package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SupplierMapper extends BaseMapperX<SupplierDO> {

    /** 列表分页，受数据范围约束。自定义 SQL 不会自动加逻辑删除条件，调用方的条件中需包含 deleted = 0。 */
    @DataScope(deptColumn = "dept_id", userColumn = "buyer_id")
    @Select("SELECT * FROM pur_supplier ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_SupplierDO")
    IPage<SupplierDO> selectScopedPage(IPage<SupplierDO> page, @Param(Constants.WRAPPER) Wrapper<SupplierDO> queryWrapper);

    /** 列表（导出），受数据范围约束 */
    @DataScope(deptColumn = "dept_id", userColumn = "buyer_id")
    @Select("SELECT * FROM pur_supplier ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_SupplierDO")
    List<SupplierDO> selectScopedList(@Param(Constants.WRAPPER) Wrapper<SupplierDO> queryWrapper);
}
