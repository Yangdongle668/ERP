package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 证书适用物料 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_certification_material")
public class CertificationMaterialDO extends BaseDO {

    private Long certificationId;
    private Long materialId;
}
