package com.erp.module.asset.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.asset.dal.dataobject.AssetDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetMapper extends BaseMapperX<AssetDO> {

    default AssetDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<AssetDO>().eq(AssetDO::getCode, code));
    }
}
