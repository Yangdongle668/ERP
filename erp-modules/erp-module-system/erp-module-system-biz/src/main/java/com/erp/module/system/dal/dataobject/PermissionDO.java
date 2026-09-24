package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 权限点目录（技术表，启动时由模块声明同步，没有审计字段） */
@Data
@TableName("sys_permission")
public class PermissionDO {

    @TableId(type = IdType.INPUT)
    private String code;
    private String moduleCode;
    private String groupCode;
    private String groupName;
    private Integer groupSort;
    private String name;
    private String permType;
    private String dependsOn;
    private Integer sort;
    private Boolean active;
    private LocalDateTime updatedAt;
}
