package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 销售预测（sal_forecast） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_forecast", autoResultMap = true)
public class SalForecastDO extends BaseDocDO {

    private String title;
    private String startPeriod;
    private String endPeriod;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime publishedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String closeReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long revisedFromId;
}
