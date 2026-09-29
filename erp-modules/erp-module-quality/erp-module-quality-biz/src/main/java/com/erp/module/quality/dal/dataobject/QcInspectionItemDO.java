package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 检验项目结果（qc_inspection_item） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_inspection_item")
public class QcInspectionItemDO extends BaseDO {

    private Long inspectionId;
    private Integer seq;
    private String itemName;
    private String itemType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String method;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String spec;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal target;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal upperLimit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal lowerLimit;
    private String defectLevel;
    private Boolean isKey;
    private Integer sampleQty;
    /** 定量：测量值 JSON 数组 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String measuredValues;
    private Integer ngCount;
    /** OK/NG */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String itemResult;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
