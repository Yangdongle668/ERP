package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** AI 异常解读（ai_anomaly） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_anomaly")
public class AiAnomalyDO extends BaseDO {

    private LocalDate detectDate;
    private String metricCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String dimension;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String dimValue;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String dimLabel;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal currentValue;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal baseValue;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal changePct;
    /** SIGMA/MOM */
    private String method;
    private String level;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String explanation;
}
