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
@TableName("sys_dict_item")
public class DictItemDO extends BaseDO {

    private String typeCode;
    @TableField("item_value")
    private String value;
    private String label;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String labelEn;
    private String tagType;
    private Integer sort;
    @TableField("is_default")
    private Boolean isDefault;
    @TableField("is_builtin")
    private Boolean builtin;
    private EnableStatus status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
