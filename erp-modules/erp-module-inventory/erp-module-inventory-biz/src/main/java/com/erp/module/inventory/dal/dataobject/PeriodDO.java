package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 库存期间 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_period")
public class PeriodDO extends BaseDO {

    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
    private String periodStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long closedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime closedAt;
    private Boolean isOpening;
    private Boolean openingCompleted;
    private Boolean financeClosed;
}
