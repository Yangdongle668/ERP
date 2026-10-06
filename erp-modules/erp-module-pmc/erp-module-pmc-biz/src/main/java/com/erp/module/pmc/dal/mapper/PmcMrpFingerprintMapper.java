package com.erp.module.pmc.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.pmc.dal.dataobject.PmcMrpFingerprintDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PmcMrpFingerprintMapper extends BaseMapperX<PmcMrpFingerprintDO> {

    /** 只保留最近一次运算的指纹（运算 ID 递增，删除更早运算的） */
    @Delete("DELETE FROM pmc_mrp_fingerprint WHERE run_id < #{runId}")
    int deleteOtherRuns(@Param("runId") Long runId);
}
