package com.erp.module.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户银行信息 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_bank")
public class CustomerBankDO extends BaseDO {

    private Long customerId;
    private String bankName;
    private String accountName;
    private String accountNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String swift;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currency;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
