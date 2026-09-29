package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 抽样方案（qc_sampling_plan） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_sampling_plan")
public class QcSamplingPlanDO extends BaseDO {

    private String code;
    private String name;
    /** GB2828/FULL/FIXED/EXEMPT */
    private String planType;
    /** S1/S2/S3/S4/I/II/III */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String inspectionLevel;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String aqlCr;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String aqlMa;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String aqlMi;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer fixedQty;
    /** ENABLED/DISABLED */
    private String planStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
