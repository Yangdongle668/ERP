package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 供应商资质（表 pur_supplier_cert） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_supplier_cert")
public class SupplierCertDO extends BaseDO {

    private Long supplierId;

    /** 字典 pur_cert_type */
    private String certType;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String certNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate issueDate;

    /** 空表示长期 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expireDate;

    /** 证书文件 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long fileId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
