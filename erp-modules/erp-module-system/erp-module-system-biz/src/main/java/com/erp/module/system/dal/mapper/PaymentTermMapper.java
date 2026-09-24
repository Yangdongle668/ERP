package com.erp.module.system.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.PaymentTermDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentTermMapper extends BaseMapperX<PaymentTermDO> {

    default PaymentTermDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<PaymentTermDO>().eq(PaymentTermDO::getCode, code));
    }
}
