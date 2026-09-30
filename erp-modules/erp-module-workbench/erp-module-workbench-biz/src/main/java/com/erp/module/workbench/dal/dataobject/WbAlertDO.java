package com.erp.module.workbench.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 预警（wb_alert） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_alert")
public class WbAlertDO extends BaseDO {

    private String alertKey;
    private String alertType;
    /** INFO/WARNING/CRITICAL */
    private String level;
    private String bizType;
    private Long bizId;
    private String title;
    private String content;
    private String route;
    /** OPEN/HANDLED/IGNORED/RESOLVED */
    private String alertStatus;
    private LocalDateTime firstRaisedAt;
    private LocalDateTime lastRaisedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long handledBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime handledAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String handleRemark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime ignoreUntil;
}
