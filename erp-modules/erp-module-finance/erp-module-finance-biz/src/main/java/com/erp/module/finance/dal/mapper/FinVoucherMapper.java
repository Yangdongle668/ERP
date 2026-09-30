package com.erp.module.finance.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.finance.dal.dataobject.FinVoucherDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FinVoucherMapper extends BaseMapperX<FinVoucherDO> {

    /** 物理删除（凭证号需要复用，不做逻辑删除） */
    @Delete("DELETE FROM fin_voucher WHERE id = #{id}")
    int deletePhysical(@Param("id") Long id);
}
