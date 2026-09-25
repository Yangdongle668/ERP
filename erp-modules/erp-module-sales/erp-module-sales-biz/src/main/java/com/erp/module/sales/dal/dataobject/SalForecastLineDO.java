package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 销售预测明细（sal_forecast_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_forecast_line", autoResultMap = true)
public class SalForecastLineDO extends BaseDO {

    private Long forecastId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    private Long materialId;
    private String period;
    private BigDecimal qty;
    private BigDecimal consumedQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
