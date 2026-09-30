package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** KPI 月度目标（bi_kpi_target） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_kpi_target")
public class BiKpiTargetDO extends BaseDO {

    private String metricCode;
    /** yyyyMM */
    private String targetMonth;
    private BigDecimal targetValue;
    private String remark;
}
