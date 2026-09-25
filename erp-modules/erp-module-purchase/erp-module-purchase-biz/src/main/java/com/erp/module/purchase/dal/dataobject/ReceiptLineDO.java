package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 到货明细（表 pur_receipt_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_receipt_line")
public class ReceiptLineDO extends BaseDO {

    private Long receiptId;

    private Integer lineNo;

    private Long orderId;

    /** 委外收货为空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderLineId;

    private Long materialId;

    private String uom;

    private BigDecimal qty;

    private BigDecimal baseQty;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierBatchNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate productionDate;

    private Boolean inspectRequired;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long targetWarehouseId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long stockInId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String stockInNo;

    private BigDecimal stockedQty;

    /** 入库确认日期 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate stockedDate;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String batchNo;

    /** NONE/PENDING/QUALIFIED/CONCESSION/REJECTED/PARTIAL */
    private String inspectStatus;

    private BigDecimal qualifiedQty;

    private BigDecimal concessionQty;

    private BigDecimal rejectedQty;

    private BigDecimal returnedQty;

    private BigDecimal statementQty;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String inspectionNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate judgedDate;

    /** 入库被退回原因 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rejectReason;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
