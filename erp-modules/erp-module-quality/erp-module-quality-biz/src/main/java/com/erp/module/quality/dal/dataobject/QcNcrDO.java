package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** NCR 不合格品报告（qc_ncr） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_ncr")
public class QcNcrDO extends BaseDocDO {

    /** IQC/IPQC/FQC/OQC/RETURN/RECHECK/PRODUCTION/INVENTORY/COMPLAINT */
    private String ncrSource;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long inspectionId;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String batchNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long supplierId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    private BigDecimal ncrQty;
    private String defectDescription;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String defectCodes;
    /** CRITICAL/MAJOR/MINOR */
    private String severity;
    /** 字典 qc_ncr_responsibility */
    private String responsibility;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String containment;
    private Boolean capaRequired;
    private Boolean scarRequired;
    /** 涉及金额（本位币） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal amountBase;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long capaId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long scarId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long complaintId;
    /** 致命缺陷已冻结批次 */
    private Boolean batchFrozen;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime approvedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime closedAt;
}
