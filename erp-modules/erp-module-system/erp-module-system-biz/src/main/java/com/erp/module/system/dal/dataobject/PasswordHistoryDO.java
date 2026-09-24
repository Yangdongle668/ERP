package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 历史密码（技术表） */
@Data
@TableName("sys_password_history")
public class PasswordHistoryDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String passwordHash;
    private LocalDateTime createdAt;
}
