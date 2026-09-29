package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** CAPA / 8D（qc_capa） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_capa")
public class QcCapaDO extends BaseDocDO {

    private String title;
    /** NCR/COMPLAINT/AUDIT/OTHER */
    private String capaSource;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long supplierId;
    private Long leaderId;
    /** 小组成员用户 ID，逗号分隔 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String teamMembers;
    /** D1 小组说明 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String d1Team;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String d2Problem;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String d3Containment;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate d3Due;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime d3DoneAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String d4RootCause;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String d4Method;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String d5Actions;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String d6Implementation;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String d7Prevention;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String d8Summary;
    /** 当前步骤 1～8（之前的步骤已完成）；9 = 全部完成 */
    private Integer currentStep;
    private LocalDate dueDate;
    /** EFFECTIVE/INEFFECTIVE */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String verifyResult;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long verifyBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime verifyAt;
    /** 验证无效次数 */
    private Integer invalidCount;
    /** 历次验证记录 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String verifyHistory;
    /** OPEN/VERIFYING/CLOSED/CANCELED */
    private String capaStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime closedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cancelReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate lastRemindDate;
}
