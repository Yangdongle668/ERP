package com.erp.module.fx.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.fx.dal.dataobject.FxDailyRateDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface FxDailyRateMapper extends BaseMapperX<FxDailyRateDO> {

    default FxDailyRateDO selectByKey(String pair, LocalDate date) {
        return selectOne(new LambdaQueryWrapper<FxDailyRateDO>().eq(FxDailyRateDO::getPair, pair).eq(FxDailyRateDO::getRateDate, date));
    }

    default List<FxDailyRateDO> selectRange(String pair, LocalDate from, LocalDate to) {
        return selectList(new LambdaQueryWrapper<FxDailyRateDO>().eq(pair != null, FxDailyRateDO::getPair, pair)
                .ge(FxDailyRateDO::getRateDate, from).le(FxDailyRateDO::getRateDate, to)
                .orderByDesc(FxDailyRateDO::getRateDate).orderByAsc(FxDailyRateDO::getPair));
    }

    @Delete("DELETE FROM fx_daily_rate WHERE rate_date < #{before}")
    int purge(@Param("before") LocalDate before);
}
