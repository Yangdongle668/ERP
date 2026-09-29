package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 检验标准（同一编号多版本）（qc_standard） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_standard")
public class QcStandardDO extends BaseDO {

    /** 编码规则 QC_STANDARD，各版本相同 */
    private String code;
    private String name;
    /** IQC/IPQC/FQC/OQC/RETURN */
    private String inspectType;
    /** MATERIAL/CATEGORY */
    private String scopeType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long materialId;
    /** CATEGORY 且为空 = 通用标准 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;
    /** IPQC 适用工序（字典 eng_operation） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operation;
    private Long samplingPlanId;
    private Integer stdVersion;
    /** DRAFT/EFFECTIVE/OBSOLETE */
    private String stdStatus;
    /** 检验规范文件（SIP） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long fileId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime effectiveAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
