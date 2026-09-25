package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

/** 项目任务 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_project_task")
public class ProjectTaskDO extends BaseDO {

    private Long projectId;
    private String stage;
    private String name;
    private Long ownerId;
    private LocalDate planStart;
    private LocalDate planEnd;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate actualEnd;
    /** TODO/DOING/DONE/CANCELED */
    private String taskStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String deliverable;
    /** 权重 1～10 */
    private Integer weight;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
