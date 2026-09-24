package com.erp.module.engineering.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.SourceType;
import com.erp.module.engineering.api.material.Tracking;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 物料分页查询条件（需求 05-02 3.1） */
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialPageReqVO extends PageParam {

    /** 编码前缀匹配 */
    private String code;
    /** 名称或规格模糊匹配 */
    private String name;
    private MaterialType materialType;
    /** 物料类型，逗号分隔 */
    private String types;
    /** 类别：包含其下级类别 */
    private Long categoryId;
    private MaterialStatus status;
    private String mpn;
    private SourceType sourceType;
    private Long buyerId;
    private Tracking tracking;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    /** 编码前缀或名称/规格模糊（选择器使用） */
    private String keyword;
    /** 排序字段：code / name / updatedAt，默认编码升序 */
    private String sortField;
    private String sortOrder;
    /** 导出：可见列（逗号分隔） */
    private String columns;
}
