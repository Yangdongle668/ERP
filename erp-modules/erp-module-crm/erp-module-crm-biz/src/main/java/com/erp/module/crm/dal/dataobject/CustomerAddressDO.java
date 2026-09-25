package com.erp.module.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户地址 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_address")
public class CustomerAddressDO extends BaseDO {

    private Long customerId;
    /** SHIP_TO/BILL_TO/NOTIFY */
    private String addressType;
    private String companyName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;
    private String country;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String province;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String city;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String zip;
    private String addressLine;
    private Boolean isDefault;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
