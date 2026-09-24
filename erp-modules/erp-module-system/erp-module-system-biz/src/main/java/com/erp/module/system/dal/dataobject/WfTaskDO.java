package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 审批任务（wf_task） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_task")
public class WfTaskDO extends BaseDO {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String TRANSFERRED = "TRANSFERRED";
    public static final String CANCELED = "CANCELED";
    public static final String AUTO_PASSED = "AUTO_PASSED";

    private Long instanceId;
    private Integer nodeSeq;
    private String nodeName;
    private String multiMode;
    private Long assigneeId;
    private String status;
    @TableField("comment_text")
    private String comment;
    private Long transferToId;
    private String autoReason;
    private LocalDateTime handledAt;
    private Long handledBy;
}
