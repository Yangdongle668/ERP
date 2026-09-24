package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.Tracking;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_material_category")
public class MaterialCategoryDO extends BaseDO {

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;
    private String code;
    private String name;
    private String codePrefix;
    private MaterialType defaultMaterialType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String defaultBaseUom;
    private Tracking defaultTracking;
    private Boolean defaultIqcRequired;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer defaultShelfLifeDays;
    private String path;
    private Integer level;
    private Integer sort;
    private EnableStatus status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
