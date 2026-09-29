package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 报工单（mfg_report） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "mfg_report", autoResultMap = true)
public class MfgReportDO extends BaseDocDO {

    private Long prodOrderId;
    private Integer operationSeq;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long workOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long workCenterId;
    private LocalDate reportDate;
    private String shift;
    /** NORMAL/REPAIR 返修补报/SCRAP 报废补报 */
    private String reportKind;
    /** 补报来源不良记录 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long defectId;
    private BigDecimal goodQty;
    private BigDecimal defectQty;
    private BigDecimal scrapQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String scrapReason;
    private BigDecimal workHours;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal machineHours;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long toolingId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime startTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime endTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime approvedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long approvedBy;
}
