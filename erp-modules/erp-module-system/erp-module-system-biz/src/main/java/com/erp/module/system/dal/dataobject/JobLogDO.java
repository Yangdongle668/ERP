package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 定时任务执行日志（只增的技术表，保留 90 天） */
@Data
@TableName("sys_job_log")
public class JobLogDO {

    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String jobCode;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String result;
    private String message;
    private String triggerType;
    private Long operatorId;
    private String node;
}
