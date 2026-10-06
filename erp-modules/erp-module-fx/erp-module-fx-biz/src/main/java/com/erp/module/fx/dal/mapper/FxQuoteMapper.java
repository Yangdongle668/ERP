package com.erp.module.fx.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.fx.dal.dataobject.FxQuoteDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FxQuoteMapper extends BaseMapperX<FxQuoteDO> {

    default boolean exists(String pair, LocalDateTime publishTime) {
        return selectCount(new LambdaQueryWrapper<FxQuoteDO>().eq(FxQuoteDO::getPair, pair).eq(FxQuoteDO::getPublishTime, publishTime)) > 0;
    }

    default List<FxQuoteDO> selectByDate(String pair, LocalDate date) {
        return selectList(new LambdaQueryWrapper<FxQuoteDO>().eq(FxQuoteDO::getPair, pair).eq(FxQuoteDO::getQuoteDate, date)
                .orderByAsc(FxQuoteDO::getPublishTime));
    }

    default FxQuoteDO selectLatest(String pair) {
        return selectOne(new LambdaQueryWrapper<FxQuoteDO>().eq(FxQuoteDO::getPair, pair).orderByDesc(FxQuoteDO::getPublishTime).last("LIMIT 1"));
    }

    /** 有报价的日期（结算漏掉的日平均汇率） */
    default List<FxQuoteDO> selectSince(LocalDate from) {
        return selectList(new LambdaQueryWrapper<FxQuoteDO>().select(FxQuoteDO::getPair, FxQuoteDO::getQuoteDate).ge(FxQuoteDO::getQuoteDate, from));
    }

    /** 保留期外的数据物理删除（R06：保存 3 年） */
    @Delete("DELETE FROM fx_quote WHERE quote_date < #{before}")
    int purge(@Param("before") LocalDate before);
}
