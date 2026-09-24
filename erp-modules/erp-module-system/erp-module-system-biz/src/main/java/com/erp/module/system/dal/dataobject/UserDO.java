package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_user", autoResultMap = true)
public class UserDO extends BaseDO {

    private String username;
    private String passwordHash;
    private String realName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String employeeNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mobile;
    private Long orgId;
    private Long deptId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String position;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long superiorUserId;
    private String gender;
    private Long avatarFileId;
    private String language;
    @TableField("is_admin")
    private Boolean admin;
    private Boolean mustChangePassword;
    private LocalDateTime passwordChangedAt;
    private Integer tokenVersion;
    private EnableStatus status;
    private Integer failCount;
    private LocalDateTime lockUntil;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
