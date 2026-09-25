package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 供应商银行账户（表 pur_supplier_bank） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_supplier_bank")
public class SupplierBankDO extends BaseDO {

    private Long supplierId;

    private String bankName;

    private String accountName;

    private String accountNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String swift;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currency;

    private Boolean isDefault;
}
