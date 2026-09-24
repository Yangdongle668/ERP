package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 审批分支（wf_branch） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_branch")
public class WfBranchDO extends BaseDO {

    public static final int DEFAULT_PRIORITY = 9999;

    private Long definitionId;
    private Integer priority;
    private String name;
    private Boolean isDefault;
    /** JSON：[{field,op,value}] */
    private String conditions;
}
