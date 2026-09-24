package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.system.enums.TermUsage;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_payment_term")
public class PaymentTermDO extends BaseDO {

    private String code;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nameEn;
    private String settlementMethod;
    @TableField("term_usage")
    private TermUsage usage;
    private EnableStatus status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
