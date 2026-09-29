package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 检验缺陷明细（qc_inspection_defect） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_inspection_defect")
public class QcInspectionDefectDO extends BaseDO {

    private Long inspectionId;
    private String defectCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String defectName;
    private String defectLevel;
    private Integer qty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String imageFileIds;
}
