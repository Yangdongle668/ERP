package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 报价（表 pur_rfq_quote） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_rfq_quote")
public class RfqQuoteDO extends BaseDO {

    private Long rfqId;

    private Long rfqLineId;

    private Long supplierId;

    private BigDecimal price;

    private BigDecimal taxRate;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal moq;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer leadTimeDays;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate validUntil;

    private Boolean isAwarded;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal awardQtyPct;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
