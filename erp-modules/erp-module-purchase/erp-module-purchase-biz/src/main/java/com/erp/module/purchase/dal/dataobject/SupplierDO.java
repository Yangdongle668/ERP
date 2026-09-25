package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.purchase.api.supplier.SupplierStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 供应商（表 pur_supplier） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_supplier", autoResultMap = true)
public class SupplierDO extends BaseDO {

    private String code;

    private String name;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nameEn;

    private String shortName;

    /** 字典 pur_supplier_type */
    private String supplierType;

    /** 字典 pur_supplier_level */
    private String supplierLevel;

    /** POTENTIAL/PENDING/QUALIFIED/SUSPENDED/ELIMINATED */
    private SupplierStatus supplierStatus;

    private String country;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String province;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String city;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String address;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String taxNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String website;

    /** 负责采购员 */
    private Long buyerId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orgId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deptId;

    private String currency;

    private Long paymentTermId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tradeTerm;

    private BigDecimal purchaseTaxRate;

    private String invoiceType;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer leadTimeDays;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate qualifiedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String suspendReason;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
