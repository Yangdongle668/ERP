package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 检验项目库（qc_inspection_item_lib） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_inspection_item_lib")
public class QcInspectionItemLibDO extends BaseDO {

    private String code;
    private String name;
    /** QUALITATIVE 定性 / QUANTITATIVE 定量 */
    private String itemType;
    /** 字典 qc_inspection_method */
    private String method;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unit;
    /** CR/MA/MI */
    private String defectLevel;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tool;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    /** ENABLED/DISABLED */
    private String itemStatus;
}
