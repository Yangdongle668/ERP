package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 供应商联系人（表 pur_supplier_contact） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_supplier_contact")
public class SupplierContactDO extends BaseDO {

    private Long supplierId;

    private String name;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String title;

    /** 业务/品质/财务/工程 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactRole;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mobile;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;

    private Boolean isPrimary;
}
