package com.erp.module.system.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.DictTypeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DictTypeMapper extends BaseMapperX<DictTypeDO> {

    default DictTypeDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<DictTypeDO>().eq(DictTypeDO::getCode, code));
    }

    @Select("SELECT version FROM sys_dict_version WHERE id = 1")
    Long selectVersion();

    /** 字典任意变更后调用：前端据此刷新缓存 */
    @Update("UPDATE sys_dict_version SET version = version + 1 WHERE id = 1")
    int increaseVersion();
}
