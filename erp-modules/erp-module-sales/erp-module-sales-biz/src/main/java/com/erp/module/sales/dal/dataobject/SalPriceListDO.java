package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 销售价格表（sal_price_list） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_price_list", autoResultMap = true)
public class SalPriceListDO extends BaseDocDO {

    private String name;
    private String scope;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerLevel;
    private String currency;
    private Boolean taxIncluded;
    private LocalDate effectiveFrom;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate effectiveTo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String closeReason;
}
