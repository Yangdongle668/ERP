package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 委外单（表 pur_outsourcing） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_outsourcing", autoResultMap = true)
public class OutsourcingDO extends BaseDocDO {

    private Long supplierId;

    private Long materialId;

    private Long bomId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bomSnapshot;

    private BigDecimal qty;

    private BigDecimal processPrice;

    private BigDecimal taxRate;

    private String currency;

    private BigDecimal exchangeRate;

    private BigDecimal amount;

    private BigDecimal taxAmount;

    private BigDecimal totalAmount;

    private LocalDate requiredDate;

    private BigDecimal receivedQty;

    private BigDecimal qualifiedQty;

    private BigDecimal statementQty;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long mrpResultId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String closeReason;
}
