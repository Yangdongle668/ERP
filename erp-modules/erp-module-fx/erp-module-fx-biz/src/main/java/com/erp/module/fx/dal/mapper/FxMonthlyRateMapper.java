package com.erp.module.fx.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.fx.dal.dataobject.FxMonthlyRateDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FxMonthlyRateMapper extends BaseMapperX<FxMonthlyRateDO> {

    default FxMonthlyRateDO selectByKey(String pair, String month) {
        return selectOne(new LambdaQueryWrapper<FxMonthlyRateDO>().eq(FxMonthlyRateDO::getPair, pair).eq(FxMonthlyRateDO::getRateMonth, month));
    }

    default List<FxMonthlyRateDO> selectRecent(int limit) {
        return selectList(new LambdaQueryWrapper<FxMonthlyRateDO>().orderByDesc(FxMonthlyRateDO::getRateMonth).orderByAsc(FxMonthlyRateDO::getPair)
                .last("LIMIT " + limit));
    }

    /** rate_month 为 yyyy-MM，按字符串比较即可 */
    @Delete("DELETE FROM fx_monthly_rate WHERE rate_month < #{before}")
    int purge(@Param("before") String before);
}
