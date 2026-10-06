package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** MRP 物料结果指纹（pmc_mrp_fingerprint）：净变更运算据此判断物料结果是否变化 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_mrp_fingerprint")
public class PmcMrpFingerprintDO extends BaseDO {

    private Long runId;
    private Long materialId;
    private String fingerprint;
}
