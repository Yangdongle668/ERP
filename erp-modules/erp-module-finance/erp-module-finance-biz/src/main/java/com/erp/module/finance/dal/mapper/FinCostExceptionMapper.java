package com.erp.module.finance.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.finance.dal.dataobject.FinCostExceptionDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FinCostExceptionMapper extends BaseMapperX<FinCostExceptionDO> {

    /** 重新计算前清除本期结果（物理删除，避免与唯一键冲突） */
    @Delete("DELETE FROM fin_cost_exception WHERE period = #{period}")
    int deleteByPeriod(@Param("period") String period);
}
