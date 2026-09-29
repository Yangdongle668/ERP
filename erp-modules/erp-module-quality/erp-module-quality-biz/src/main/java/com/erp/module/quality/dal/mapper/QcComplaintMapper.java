package com.erp.module.quality.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.quality.dal.dataobject.QcComplaintDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QcComplaintMapper extends BaseMapperX<QcComplaintDO> {

    /** 客诉列表按 CRM 客户负责人 / 部门做数据范围（QC-CPL-R05）。调用方条件需包含 deleted = 0。 */
    @DataScope(orgColumn = "", deptColumn = "sales_dept_id", userColumn = "sales_owner_id")
    @Select("SELECT * FROM qc_complaint ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_QcComplaintDO")
    IPage<QcComplaintDO> selectScopedPage(IPage<QcComplaintDO> page, @Param(Constants.WRAPPER) Wrapper<QcComplaintDO> queryWrapper);

    @DataScope(orgColumn = "", deptColumn = "sales_dept_id", userColumn = "sales_owner_id")
    @Select("SELECT * FROM qc_complaint ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_QcComplaintDO")
    List<QcComplaintDO> selectScopedList(@Param(Constants.WRAPPER) Wrapper<QcComplaintDO> queryWrapper);
}
