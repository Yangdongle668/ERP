package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.system.api.param.ParamType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_param")
public class ParamDO extends BaseDO {

    private String paramKey;
    private String moduleCode;
    private String groupName;
    private String name;
    private ParamType valueType;
    /** ENUM 选项 JSON：[{value, label}] */
    private String options;
    private String minValue;
    private String maxValue;
    @TableField(value = "param_value", updateStrategy = FieldStrategy.ALWAYS)
    private String value;
    private String defaultValue;
    private String description;
    private Integer sort;
    private Boolean active;
}
