package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 订单执行记录（sal_order_exec） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_order_exec", autoResultMap = true)
public class SalOrderExecDO extends BaseDO {

    private Long orderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderLineId;
    private String execType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String docType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long docId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String docNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal qty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal amount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate execDate;
}
