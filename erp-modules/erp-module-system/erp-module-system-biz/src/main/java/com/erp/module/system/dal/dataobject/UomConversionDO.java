package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_uom_conversion")
public class UomConversionDO extends BaseDO {

    private String fromUom;
    private String toUom;
    /** 1 源单位 = rate 目标单位 */
    private BigDecimal rate;
}
