package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.system.enums.UomCategory;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_uom")
public class UomDO extends BaseDO {

    private String code;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nameEn;
    private UomCategory category;
    @TableField("qty_precision")
    private Integer precision;
    private Integer sort;
    @TableField("is_builtin")
    private Boolean builtin;
    private EnableStatus status;
}
