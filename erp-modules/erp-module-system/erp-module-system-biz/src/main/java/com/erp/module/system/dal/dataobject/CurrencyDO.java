package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_currency")
public class CurrencyDO extends BaseDO {

    private String code;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nameEn;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String symbol;
    private Integer amountPrecision;
    @TableField("is_base")
    private Boolean base;
    private Integer sort;
    private EnableStatus status;
}
