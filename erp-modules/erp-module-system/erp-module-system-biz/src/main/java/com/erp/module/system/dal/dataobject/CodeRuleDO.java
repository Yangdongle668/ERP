package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
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
    private String moduleCode;
    private String prefix;
    private String datePattern;
    @TableField("seq_separator")
    private String separator;
    private Integer seqLength;
    private ResetCycle resetCycle;
    private Boolean allowManual;
    /** 前缀可用变量，逗号分隔（由模块声明同步） */
    private String allowedVars;
}
