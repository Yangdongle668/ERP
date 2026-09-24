package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 批次档案 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_batch")
public class BatchDO extends BaseDO {

    private Long materialId;
    private String batchNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long supplierId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierBatchNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate productionDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expireDate;
    private LocalDate firstInDate;
    private LocalDateTime firstInAt;
    private String sourceType;
    private String sourceNo;
    private Boolean isConcession;
    private Boolean frozen;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String frozenReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String frozenByModule;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String frozenSourceNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
