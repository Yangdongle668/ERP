package com.erp.module.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户转移记录 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_transfer_log")
public class CustomerTransferLogDO extends BaseDO {

    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long fromOwnerId;
    private Long toOwnerId;
    private Boolean transferDocs;
    private String reason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long operatorId;
}
