package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 回款计划（sal_payment_plan） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_payment_plan", autoResultMap = true)
public class SalPaymentPlanDO extends BaseDO {

    private Long orderId;
    private Integer seq;
    private Integer batchNo;
    private String nodeName;
    private BigDecimal percent;
    private String baseEvent;
    private Integer days;
    private BigDecimal planAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long shipmentId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate eventDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate dueDate;
    private BigDecimal receivedAmount;
    private String planStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate promisedPayDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime followedAt;
}
