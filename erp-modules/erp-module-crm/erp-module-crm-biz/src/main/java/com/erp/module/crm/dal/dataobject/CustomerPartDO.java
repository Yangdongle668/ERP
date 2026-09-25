package com.erp.module.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户料号对照 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_part")
public class CustomerPartDO extends BaseDO {

    private Long customerId;
    private String customerPartNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPartName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPartSpec;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerRevision;
    /** ENABLED/DISABLED */
    private String partStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
