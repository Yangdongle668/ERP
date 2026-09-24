package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 打印模板（sys_print_template） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_print_template")
public class PrintTemplateDO extends BaseDO {

    private String bizType;
    private String name;
    private String language;
    private String paper;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer paperWidth;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer paperHeight;
    private String margin;
    private String content;
    private Boolean isDefault;
    private Boolean isBuiltin;
    private EnableStatus status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
