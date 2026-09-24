package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 可审批的单据类型（由 ApprovalBizDefinition 同步）（wf_biz_type） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_biz_type")
public class WfBizTypeDO extends BaseDO {

    private String bizType;
    private String name;
    private String moduleCode;
    private String detailRoute;
    /** JSON：[{code,name,type,options,dictType}] */
    private String fields;
    /** JSON：[{code,name}] */
    private String userFields;
    private Boolean active;
}
