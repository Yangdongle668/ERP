package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 预测冲销记录（sal_forecast_consumption） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_forecast_consumption", autoResultMap = true)
public class SalForecastConsumptionDO extends BaseDO {

    private Long forecastLineId;
    private Long orderLineId;
    private BigDecimal qty;
}
