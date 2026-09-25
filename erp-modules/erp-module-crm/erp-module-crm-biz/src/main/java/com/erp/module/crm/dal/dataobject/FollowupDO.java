package com.erp.module.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 跟进记录 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_followup")
public class FollowupDO extends BaseDO {

    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long contactId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long opportunityId;
    /** 字典 crm_followup_type */
    private String followupType;
    private LocalDateTime followupAt;
    private String subject;
    private String content;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate nextFollowupAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nextPlan;
    private Long ownerId;
    private Boolean reminded;
}
