package com.erp.module.bi.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.bi.dal.dataobject.BiAggInventorySnapshotDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface BiAggInventorySnapshotMapper extends BaseMapperX<BiAggInventorySnapshotDO> {

    /** 重算区间前清除（物理删除） */
    @Delete("DELETE FROM bi_agg_inventory_daily_snapshot WHERE stat_date BETWEEN #{from} AND #{to}")
    int deleteRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
