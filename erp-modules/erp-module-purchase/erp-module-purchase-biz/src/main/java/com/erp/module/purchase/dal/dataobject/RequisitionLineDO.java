package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 采购申请明细（表 pur_requisition_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_requisition_line")
public class RequisitionLineDO extends BaseDO {

    private Long requisitionId;

    private Integer lineNo;

    private Long materialId;

    private String uom;

    private BigDecimal qty;

    private BigDecimal baseQty;

    private LocalDate requiredDate;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long suggestedSupplierId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal referencePrice;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String purpose;

    private BigDecimal orderedQty;

    /** OPEN/ORDERED/CLOSED */
    private String lineStatus;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long mrpResultId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceDemand;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
