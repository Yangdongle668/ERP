package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 产能日历（例外日）（pmc_capacity_calendar） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_capacity_calendar")
public class PmcCapacityCalendarDO extends BaseDO {

    /** 0 表示全厂（节假日） */
    private Long workCenterId;
    private LocalDate calDate;
    private BigDecimal availableHours;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reason;
}
