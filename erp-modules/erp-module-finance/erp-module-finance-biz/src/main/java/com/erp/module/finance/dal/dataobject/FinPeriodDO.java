package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 会计期间（fin_period） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_period")
public class FinPeriodDO extends BaseDO {

    /** yyyyMM */
    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
    /** NOT_OPEN/OPEN/CLOSED */
    private String periodStatus;
    /** 成本已锁定 */
    private Boolean costLocked;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long closedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime closedAt;
}
