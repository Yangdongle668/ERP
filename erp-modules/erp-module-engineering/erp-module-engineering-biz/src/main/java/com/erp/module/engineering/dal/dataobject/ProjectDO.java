package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 研发项目 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_project")
public class ProjectDO extends BaseDocDO {

    private String name;
    /** NPI/IMPROVEMENT/CUSTOMER_CUSTOM */
    private String projectType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long productMaterialId;
    /** 项目经理 */
    private Long pmUserId;
    /** 字典 eng_project_stage */
    private String stage;
    private LocalDate planStart;
    private LocalDate planEnd;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate actualStart;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate actualEnd;
    /** 进度（0～1） */
    private BigDecimal progressPct;
    /** HIGH/MEDIUM/LOW */
    private String priority;
    /** PLANNING/IN_PROGRESS/ON_HOLD/COMPLETED/CANCELED */
    private String projectStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cancelReason;
}
