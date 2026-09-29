package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 不良记录（mfg_defect） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "mfg_defect", autoResultMap = true)
public class MfgDefectDO extends BaseDO {

    private Long reportId;
    private Long prodOrderId;
    private Integer operationSeq;
    private Long materialId;
    private LocalDate reportDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deptId;
    private String defectCode;
    private BigDecimal qty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String position;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String imageFileIds;
    /** PENDING/REPAIRED/SCRAPPED */
    private String disposition;
    private BigDecimal repairedQty;
    private BigDecimal scrappedQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ncrNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long handledBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime handledAt;
}
