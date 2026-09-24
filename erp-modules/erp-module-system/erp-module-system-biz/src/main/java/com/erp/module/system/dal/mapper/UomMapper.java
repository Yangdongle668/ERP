package com.erp.module.system.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.UomDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UomMapper extends BaseMapperX<UomDO> {

    default UomDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<UomDO>().eq(UomDO::getCode, code));
    }

    default UomDO selectByName(String name) {
        return selectOne(new LambdaQueryWrapper<UomDO>().eq(UomDO::getName, name));
    }
}
