package com.erp.module.pmc.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.pmc.dal.dataobject.PmcDeliveryAlertDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PmcDeliveryAlertMapper extends BaseMapperX<PmcDeliveryAlertDO> {

    /** 预警列表按销售订单的业务员 / 部门做数据范围（PMC-ALT-R03：业务员只看自己订单）。调用方条件需包含 deleted = 0。 */
    @DataScope(orgColumn = "", deptColumn = "sales_dept_id", userColumn = "sales_owner_id")
    @Select("SELECT * FROM pmc_delivery_alert ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_PmcDeliveryAlertDO")
    IPage<PmcDeliveryAlertDO> selectScopedPage(IPage<PmcDeliveryAlertDO> page, @Param(Constants.WRAPPER) Wrapper<PmcDeliveryAlertDO> queryWrapper);

    @DataScope(orgColumn = "", deptColumn = "sales_dept_id", userColumn = "sales_owner_id")
    @Select("SELECT * FROM pmc_delivery_alert ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_PmcDeliveryAlertDO")
    List<PmcDeliveryAlertDO> selectScopedList(@Param(Constants.WRAPPER) Wrapper<PmcDeliveryAlertDO> queryWrapper);
}
