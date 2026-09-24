package com.erp.module.system.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.CodeRuleDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CodeRuleMapper extends BaseMapperX<CodeRuleDO> {

    default CodeRuleDO selectByBizCode(String bizCode) {
        return selectOne(new LambdaQueryWrapper<CodeRuleDO>().eq(CodeRuleDO::getBizCode, bizCode));
    }
}
