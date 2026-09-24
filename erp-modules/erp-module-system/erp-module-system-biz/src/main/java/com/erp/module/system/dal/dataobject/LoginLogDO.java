package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 登录日志（只增不改的技术表） */
@Data
@TableName("sys_login_log")
public class LoginLogDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String username;
    private Long userId;
    private String realName;
    private String logType;
    private String result;
    private String ip;
    private String userAgent;
    private String browser;
    private String os;
    private LocalDateTime createdAt;
}
