package com.erp.module.system.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.ParamDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ParamMapper extends BaseMapperX<ParamDO> {

    default ParamDO selectByKey(String key) {
        return selectOne(new LambdaQueryWrapper<ParamDO>().eq(ParamDO::getParamKey, key));
    }
}
