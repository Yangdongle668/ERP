package com.erp.module.engineering.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialPageReqVO extends PageParam {

    /** 编码前缀匹配 */
    private String code;
    /** 名称模糊匹配 */
    private String name;
    private MaterialType materialType;
    private Long categoryId;
    private MaterialStatus status;
    /** 编码前缀或名称/规格模糊（选择器使用） */
    private String keyword;
    /** 物料类型，逗号分隔（选择器使用） */
    private String types;
}
