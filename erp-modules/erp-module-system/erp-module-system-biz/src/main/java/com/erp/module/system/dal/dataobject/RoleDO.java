package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.system.enums.DataScopeType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class RoleDO extends BaseDO {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    private String code;
    private String name;
    private DataScopeType dataScope;
    @TableField("is_builtin")
    private Boolean builtin;
    private Integer sort;
    private EnableStatus status;
    private String remark;
}
