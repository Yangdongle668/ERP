package com.erp.module.crm.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.crm.dal.dataobject.CustomerCreditDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerCreditMapper extends BaseMapperX<CustomerCreditDO> {

    /** 物理删除（导入回滚） */
    @org.apache.ibatis.annotations.Delete("DELETE FROM crm_customer_credit WHERE customer_id = #{id}")
    int hardDeleteByCustomer(@org.apache.ibatis.annotations.Param("id") Long id);
}
