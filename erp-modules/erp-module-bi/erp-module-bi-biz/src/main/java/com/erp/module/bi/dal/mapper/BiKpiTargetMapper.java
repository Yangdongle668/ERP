package com.erp.module.bi.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.bi.dal.dataobject.BiKpiTargetDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BiKpiTargetMapper extends BaseMapperX<BiKpiTargetDO> {

    default List<BiKpiTargetDO> selectByMonths(String fromMonth, String toMonth) {
        return selectList(new LambdaQueryWrapper<BiKpiTargetDO>().ge(BiKpiTargetDO::getTargetMonth, fromMonth).le(BiKpiTargetDO::getTargetMonth, toMonth));
    }

    /** 物理删除（清空某月目标；唯一键 指标 + 月份） */
    @Delete("DELETE FROM bi_kpi_target WHERE metric_code = #{code} AND target_month = #{month}")
    int deleteByKey(@Param("code") String code, @Param("month") String month);
}
