package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 可打印单据类型（sys_print_biz），由 PrintBizDefinition 同步 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_print_biz")
public class PrintBizDO extends BaseDO {

    private String bizType;
    private String name;
    private String moduleCode;
    private String dataApi;
    /** JSON：[{path,name,type}] */
    private String variables;
    private String sampleData;
    private Boolean active;
}
