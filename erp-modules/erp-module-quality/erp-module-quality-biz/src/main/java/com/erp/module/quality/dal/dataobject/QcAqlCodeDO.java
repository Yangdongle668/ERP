package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** AQL 样本量字码表（qc_aql_code）：批量范围 × 检验水平 → 字码 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_aql_code")
public class QcAqlCodeDO extends BaseDO {

    private Long lotMin;
    /** 空表示及以上 */
    private Long lotMax;
    private String inspectionLevel;
    private String codeLetter;
}
