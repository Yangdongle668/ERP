package com.erp.module.workbench.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 待办（wb_todo） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_todo")
public class WbTodoDO extends BaseDO {

    private String todoKey;
    private Long userId;
    /** APPROVAL/TASK */
    private String category;
    private String bizType;
    private Long bizId;
    private String bizNo;
    private String title;
    private String route;
    /** HIGH/NORMAL/LOW */
    private String priority;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime dueTime;
    /** PENDING/DONE/CANCELED */
    private String todoStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime doneAt;
}
