package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 生效采购价格（表 pur_price） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_price")
public class PriceDO extends BaseDO {

    private Long supplierId;

    private Long materialId;

    private String currency;

    private BigDecimal minQty;

    /** 不含税单价（基本单位） */
    private BigDecimal price;

    private BigDecimal taxRate;

    private BigDecimal priceInclTax;

    private LocalDate effectiveFrom;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate effectiveTo;

    /** EFFECTIVE/EXPIRED/REPLACED */
    private String priceStatus;

    private Long adjustId;

    private Long adjustLineId;
}
