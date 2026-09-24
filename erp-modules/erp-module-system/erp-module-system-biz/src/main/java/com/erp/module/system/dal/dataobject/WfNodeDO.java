package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 审批节点（wf_node） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_node")
public class WfNodeDO extends BaseDO {

    private Long branchId;
    private Integer seq;
    private String name;
    private String approverType;
    /** USER：用户 ID 数组；ROLE：{roleId,sameCompany}；BIZ_USER：字段编码 */
    private String approverValue;
    private String multiMode;
}
