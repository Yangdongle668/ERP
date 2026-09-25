package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 可供物料（表 pur_supplier_material） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_supplier_material")
public class SupplierMaterialDO extends BaseDO {

    private Long supplierId;

    private Long materialId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierPartNo;

    /** TRIAL/QUALIFIED/DISABLED */
    private String supplyStatus;

    private Boolean isDefault;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer leadTimeDays;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal moq;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal mpq;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal quotaPct;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate approvedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
