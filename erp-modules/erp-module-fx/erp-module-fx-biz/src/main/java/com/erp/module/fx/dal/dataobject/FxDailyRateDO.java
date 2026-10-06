package com.erp.module.fx.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fx_daily_rate")
public class FxDailyRateDO extends BaseDO {

    private String pair;
    private LocalDate rateDate;
    private BigDecimal avgRate;
    private BigDecimal minRate;
    private BigDecimal maxRate;
    private Integer sampleCount;
    private Boolean finalized;
}
