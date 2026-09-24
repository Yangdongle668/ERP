package com.erp.module.system.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.DictItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DictItemMapper extends BaseMapperX<DictItemDO> {

    default List<DictItemDO> selectByType(String typeCode) {
        return selectList(new LambdaQueryWrapper<DictItemDO>().eq(DictItemDO::getTypeCode, typeCode)
                .orderByAsc(DictItemDO::getSort).orderByAsc(DictItemDO::getId));
    }

    default DictItemDO selectByValue(String typeCode, String value) {
        return selectOne(new LambdaQueryWrapper<DictItemDO>().eq(DictItemDO::getTypeCode, typeCode).eq(DictItemDO::getValue, value));
    }

    default long countByType(String typeCode) {
        return selectCount(new LambdaQueryWrapper<DictItemDO>().eq(DictItemDO::getTypeCode, typeCode));
    }
}
