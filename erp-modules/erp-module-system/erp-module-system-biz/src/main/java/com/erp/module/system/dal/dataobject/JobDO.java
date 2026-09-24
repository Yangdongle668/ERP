package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 定时任务（sys_job），由 @ErpJob 声明同步 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job")
public class JobDO extends BaseDO {

    private String code;
    private String name;
    private String moduleCode;
    private String cron;
    private String defaultCron;
    private Boolean enabled;
    private Boolean active;
    private LocalDateTime lastRunAt;
    private String lastResult;
    private String lastMessage;
    /** 停用时需要置空，更新时始终写入 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime nextRunAt;
    private LocalDateTime lockedUntil;
    private String lockedBy;
}
