package com.erp.module.fx.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fx_monthly_rate")
public class FxMonthlyRateDO extends BaseDO {

    private String pair;
    private String rateMonth;
    private BigDecimal avgRate;
    private Integer dayCount;
}
