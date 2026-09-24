package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作日志（只增不改的技术表） */
@Data
@TableName("sys_oper_log")
public class OperLogDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String traceId;
    private String moduleCode;
    private String action;
    private String method;
    private String path;
    private String params;
    private String result;
    private Integer errorCode;
    private String errorMsg;
    private Integer durationMs;
    private Long userId;
    private String username;
    private String realName;
    private String ip;
    private LocalDateTime createdAt;
}
