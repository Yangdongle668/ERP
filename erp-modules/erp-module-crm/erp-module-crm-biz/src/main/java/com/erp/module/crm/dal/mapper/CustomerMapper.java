package com.erp.module.crm.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.crm.dal.dataobject.CustomerDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CustomerMapper extends BaseMapperX<CustomerDO> {

    /** 列表分页，受数据范围约束（负责部门 / 负责人）。自定义 SQL 不会自动加逻辑删除条件，条件中需包含 deleted = 0。 */
    @DataScope(deptColumn = "dept_id", userColumn = "owner_id")
    @Select("SELECT * FROM crm_customer ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_CustomerDO")
    IPage<CustomerDO> selectScopedPage(IPage<CustomerDO> page, @Param(Constants.WRAPPER) Wrapper<CustomerDO> queryWrapper);

    @DataScope(deptColumn = "dept_id", userColumn = "owner_id")
    @Select("SELECT * FROM crm_customer ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_CustomerDO")
    List<CustomerDO> selectScopedList(@Param(Constants.WRAPPER) Wrapper<CustomerDO> queryWrapper);
}
