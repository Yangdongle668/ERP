package com.erp.module.system.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.CurrencyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CurrencyMapper extends BaseMapperX<CurrencyDO> {

    default CurrencyDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<CurrencyDO>().eq(CurrencyDO::getCode, code));
    }

    default CurrencyDO selectBase() {
        return selectOne(new LambdaQueryWrapper<CurrencyDO>().eq(CurrencyDO::getBase, true));
    }

    /** 设置本位币时先清除原本位币标记 */
    @Update("UPDATE sys_currency SET is_base = 0, updated_at = CURRENT_TIMESTAMP WHERE is_base = 1")
    int clearBase();
}
