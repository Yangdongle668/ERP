package com.erp.module.shipping.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.shipping.dal.dataobject.ShpShipmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShpShipmentMapper extends BaseMapperX<ShpShipmentDO> {

    /** 列表分页，受数据范围约束（dept_id 车间 / owner_id）。自定义 SQL 不会自动加逻辑删除条件，调用方的条件中需包含 deleted = 0。 */
    @DataScope
    @Select("SELECT * FROM shp_shipment ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_ShpShipmentDO")
    IPage<ShpShipmentDO> selectScopedPage(IPage<ShpShipmentDO> page, @Param(Constants.WRAPPER) Wrapper<ShpShipmentDO> queryWrapper);

    /** 列表（导出、报表），受数据范围约束 */
    @DataScope
    @Select("SELECT * FROM shp_shipment ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_ShpShipmentDO")
    List<ShpShipmentDO> selectScopedList(@Param(Constants.WRAPPER) Wrapper<ShpShipmentDO> queryWrapper);
}
