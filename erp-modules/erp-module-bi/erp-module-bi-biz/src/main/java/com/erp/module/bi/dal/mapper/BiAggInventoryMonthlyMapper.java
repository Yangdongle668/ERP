package com.erp.module.bi.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.bi.dal.dataobject.BiAggInventoryMonthlyDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BiAggInventoryMonthlyMapper extends BaseMapperX<BiAggInventoryMonthlyDO> {

    /** 重算期间前清除（物理删除） */
    @Delete("DELETE FROM bi_agg_inventory_monthly WHERE period BETWEEN #{from} AND #{to}")
    int deleteRange(@Param("from") String from, @Param("to") String to);
}
