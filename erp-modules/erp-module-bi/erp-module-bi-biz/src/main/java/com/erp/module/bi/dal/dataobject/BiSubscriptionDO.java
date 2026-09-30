package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 报表订阅（bi_subscription） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_subscription")
public class BiSubscriptionDO extends BaseDO {

    private Long userId;
    private String subName;
    /** 指标编码，逗号分隔 */
    private String metrics;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String dimension;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String filters;
    private String periodType;
    private Integer topN;
    private String frequency;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer weekday;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer monthday;
    private Boolean sendEmail;
    private Boolean enabled;
    private LocalDate lastSentOn;
    private String lastStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastMessage;
}
