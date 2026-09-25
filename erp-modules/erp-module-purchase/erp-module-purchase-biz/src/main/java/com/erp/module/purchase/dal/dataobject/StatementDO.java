package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 供应商对账单（表 pur_statement） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_statement", autoResultMap = true)
public class StatementDO extends BaseDocDO {

    private Long supplierId;

    private LocalDate periodFrom;

    private LocalDate periodTo;

    private String currency;

    private BigDecimal exchangeRate;

    private BigDecimal goodsAmount;

    private BigDecimal returnAmount;

    private BigDecimal adjustAmount;

    private BigDecimal totalAmount;

    private BigDecimal taxAmount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime supplierConfirmedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierConfirmer;
}
