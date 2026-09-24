package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 替代料：1 个主料 = ratio 个替代料；priority 1 最高 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_bom_substitute")
public class BomSubstituteDO extends BaseDO {

    private Long bomId;
    private Long bomLineId;
    private Long substituteId;
    private Integer priority;
    private BigDecimal ratio;
    private String remark;
}
