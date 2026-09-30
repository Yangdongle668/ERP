package com.erp.module.bi.dal.mapper;

import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.bi.dal.dataobject.BiAggFinanceDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 列表类查询受数据范围约束（org_id / dept_id / owner_id）；数据任务读写时使用 DataScopes.ignore */
@DataScope(orgColumn = "org_id", deptColumn = "dept_id", userColumn = "owner_id")
@Mapper
public interface BiAggFinanceMapper extends BaseMapperX<BiAggFinanceDO> {

    /** 重算期间前清除（物理删除） */
    @Delete("DELETE FROM bi_agg_finance_monthly WHERE period BETWEEN #{from} AND #{to}")
    int deleteRange(@Param("from") String from, @Param("to") String to);
}
