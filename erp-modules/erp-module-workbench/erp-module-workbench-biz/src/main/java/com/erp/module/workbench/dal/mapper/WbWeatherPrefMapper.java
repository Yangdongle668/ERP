package com.erp.module.workbench.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.workbench.dal.dataobject.WbWeatherPrefDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WbWeatherPrefMapper extends BaseMapperX<WbWeatherPrefDO> {

    default WbWeatherPrefDO selectByUser(Long userId) {
        return selectOne(new LambdaQueryWrapper<WbWeatherPrefDO>().eq(WbWeatherPrefDO::getUserId, userId).last("LIMIT 1"));
    }
}
