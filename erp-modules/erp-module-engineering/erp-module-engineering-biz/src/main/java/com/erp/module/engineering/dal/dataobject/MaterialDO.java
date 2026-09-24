package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_material")
public class MaterialDO extends BaseDO {

    private String code;
    private String name;
    private String nameEn;
    private String spec;
    private MaterialType materialType;
    private Long categoryId;
    private String baseUom;
    private MaterialStatus status;
    private String remark;
}
