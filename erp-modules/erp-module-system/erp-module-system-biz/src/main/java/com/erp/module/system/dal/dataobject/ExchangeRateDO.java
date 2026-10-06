package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.system.api.currency.RateType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_exchange_rate")
public class ExchangeRateDO extends BaseDO {

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_IMPORT = "IMPORT";
    /** 实时汇率模块自动推送（中国银行现汇买入价） */
    public static final String SOURCE_AUTO = "AUTO";

    private String currency;
    private RateType rateType;
    private LocalDate effectiveDate;
    private BigDecimal rate;
    private String source;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
