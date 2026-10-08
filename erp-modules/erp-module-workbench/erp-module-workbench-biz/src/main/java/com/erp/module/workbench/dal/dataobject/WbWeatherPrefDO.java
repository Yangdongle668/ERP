package com.erp.module.workbench.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 用户天气设置：AUTO 自动定位 / MANUAL 手选城市 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_weather_pref")
public class WbWeatherPrefDO extends BaseDO {

    private Long userId;
    private String mode;
    private String cityName;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
