package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 客诉（qc_complaint） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "qc_complaint", autoResultMap = true)
public class QcComplaintDO extends BaseDocDO {

    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long contactId;
    /** 字典 qc_complaint_type */
    private String complaintType;
    /** CRITICAL/MAJOR/MINOR */
    private String severity;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPartNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shipmentNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String batchNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String serialNos;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal complaintQty;
    private String description;
    private LocalDateTime receivedAt;
    private LocalDate replyDueDate;
    private Long qeId;
    /** CRM 客户负责人（数据权限） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesOwnerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesDeptId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rootCause;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String replyContent;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime repliedAt;
    /** NONE/RETURN/REPLACE/CREDIT/REWORK_ONSITE */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String handling;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String handlingRemark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal claimAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal agreedAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currency;
    /** OPEN/ANALYZING/REPLIED/CLOSED/CANCELED */
    private String complaintStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long capaId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long ncrId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long returnId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cancelReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime closedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate lastRemindDate;
}
