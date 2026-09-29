package com.erp.module.production.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.production.dal.dataobject.MfgWorkOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MfgWorkOrderMapper extends BaseMapperX<MfgWorkOrderDO> {

    /** 列表分页，受数据范围约束（dept_id 车间 / owner_id）。自定义 SQL 不会自动加逻辑删除条件，调用方的条件中需包含 deleted = 0。 */
    @DataScope
    @Select("SELECT * FROM mfg_work_order ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_MfgWorkOrderDO")
    IPage<MfgWorkOrderDO> selectScopedPage(IPage<MfgWorkOrderDO> page, @Param(Constants.WRAPPER) Wrapper<MfgWorkOrderDO> queryWrapper);

    /** 列表（导出、报表），受数据范围约束 */
    @DataScope
    @Select("SELECT * FROM mfg_work_order ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_MfgWorkOrderDO")
    List<MfgWorkOrderDO> selectScopedList(@Param(Constants.WRAPPER) Wrapper<MfgWorkOrderDO> queryWrapper);
}
