package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 后台任务（sys_async_task） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_async_task")
public class AsyncTaskDO extends BaseDO {

    public static final String WAITING = "WAITING";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String CANCELED = "CANCELED";

    private String taskType;
    private String name;
    private String moduleCode;
    private String status;
    private Integer progress;
    private Long resultFileId;
    private Boolean resultExpired;
    private String resultMessage;
    private String errorMessage;
    private Long submittedBy;
    private String node;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
