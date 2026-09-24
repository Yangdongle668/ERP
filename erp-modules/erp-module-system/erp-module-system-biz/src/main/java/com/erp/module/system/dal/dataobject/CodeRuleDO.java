package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.system.api.coderule.CodeRuleDefinition.ResetCycle;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_code_rule")
public class CodeRuleDO extends BaseDO {

    private String bizCode;
    private String name;
    private String prefix;
    private String datePattern;
    private Integer seqLength;
    private ResetCycle resetCycle;
}
