package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 报工人员（mfg_report_operator） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mfg_report_operator")
public class MfgReportOperatorDO extends BaseDO {

    private Long reportId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long userId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operatorName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal hours;
}
