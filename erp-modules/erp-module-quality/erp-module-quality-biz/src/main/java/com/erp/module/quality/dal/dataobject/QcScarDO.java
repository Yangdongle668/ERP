package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** SCAR 供应商纠正措施要求（qc_scar） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_scar")
public class QcScarDO extends BaseDocDO {

    private Long supplierId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long ncrId;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String batchNo;
    private String problemDescription;
    private String requirement;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate replyDueDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime sentAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String replyContent;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime repliedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String verifyPlan;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String verifyResult;
    private Integer invalidCount;
    /** DRAFT/SENT/REPLIED/VERIFYING/CLOSED/CANCELED */
    private String scarStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cancelReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime closedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate lastRemindDate;
}
