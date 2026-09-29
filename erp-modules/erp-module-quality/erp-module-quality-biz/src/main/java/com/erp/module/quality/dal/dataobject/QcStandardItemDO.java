package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 检验标准项目（qc_standard_item） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_standard_item")
public class QcStandardItemDO extends BaseDO {

    private Long standardId;
    private Integer seq;
    private Long libItemId;
    private String name;
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
    /** 覆盖标准的抽样方案 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long samplingPlanId;
    private Boolean isKey;
}
