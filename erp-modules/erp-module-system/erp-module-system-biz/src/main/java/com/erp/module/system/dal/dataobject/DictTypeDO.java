package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class DictTypeDO extends BaseDO {

    public static final String CUSTOM_MODULE = "custom";

    private String code;
    private String name;
    private String moduleCode;
    @TableField("is_builtin")
    private Boolean builtin;
    private EnableStatus status;
    private String remark;
}
