package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 审批流程定义（每个版本一行）（wf_definition） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_definition")
public class WfDefinitionDO extends BaseDO {

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String ARCHIVED = "ARCHIVED";
    public static final String EMPTY_AUTO_PASS = "AUTO_PASS";
    public static final String EMPTY_TO_ADMIN = "TO_ADMIN";

    private String bizType;
    /** 流程版本号（version 列为乐观锁） */
    private Integer defVersion;
    private String status;
    private Boolean enabled;
    private Boolean skipInitiator;
    private Boolean skipDuplicate;
    private String emptyPolicy;
    private Integer basedOn;
    private LocalDateTime publishedAt;
    private Long publishedBy;
    private String remark;
}
