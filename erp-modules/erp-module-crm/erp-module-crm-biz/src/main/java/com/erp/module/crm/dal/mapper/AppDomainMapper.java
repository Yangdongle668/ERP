package com.erp.module.crm.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.crm.dal.dataobject.AppDomainDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppDomainMapper extends BaseMapperX<AppDomainDO> {

    default List<AppDomainDO> selectSorted() {
        return selectList(new LambdaQueryWrapper<AppDomainDO>().orderByAsc(AppDomainDO::getSort).orderByAsc(AppDomainDO::getCode));
    }

    default AppDomainDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<AppDomainDO>().eq(AppDomainDO::getCode, code).last("LIMIT 1"));
    }

    /** 物理删除：领域字母有唯一约束，删除后可以重新新增同一字母 */
    @Delete("DELETE FROM crm_app_domain WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);
}
