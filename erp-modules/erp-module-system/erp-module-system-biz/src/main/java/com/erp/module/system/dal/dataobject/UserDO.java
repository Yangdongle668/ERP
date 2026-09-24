package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class UserDO extends BaseDO {

    private String username;
    private String passwordHash;
    private String realName;
    private String email;
    private String mobile;
    private Long orgId;
    private Long deptId;
    private EnableStatus status;
    private Integer failCount;
    private LocalDateTime lockUntil;
    private LocalDateTime lastLoginAt;
}
