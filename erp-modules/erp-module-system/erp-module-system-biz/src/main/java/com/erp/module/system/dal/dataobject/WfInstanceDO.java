package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 审批实例（wf_instance） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_instance")
public class WfInstanceDO extends BaseDO {

    public static final String RUNNING = "RUNNING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String TERMINATED = "TERMINATED";

    private String bizType;
    private Long bizId;
    private String bizNo;
    private String title;
    private Long definitionId;
    private Long branchId;
    private Long initiatorId;
    private Long initiatorDeptId;
    private String variables;
    private String bizUsers;
    private String status;
    private Integer currentSeq;
    private String currentNodeName;
    private LocalDateTime nodeStartedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String resultComment;
}
