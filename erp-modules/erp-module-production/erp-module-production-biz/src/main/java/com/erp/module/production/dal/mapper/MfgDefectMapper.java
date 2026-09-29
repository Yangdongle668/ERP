package com.erp.module.production.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.production.dal.dataobject.MfgDefectDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MfgDefectMapper extends BaseMapperX<MfgDefectDO> {

    /** 不良记录分页，按车间（dept_id，取自生产订单）过滤数据范围。条件中需包含 deleted = 0 */
    @DataScope(orgColumn = "", userColumn = "")
    @Select("SELECT * FROM mfg_defect ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_MfgDefectDO")
    IPage<MfgDefectDO> selectScopedPage(IPage<MfgDefectDO> page, @Param(Constants.WRAPPER) Wrapper<MfgDefectDO> queryWrapper);
}
