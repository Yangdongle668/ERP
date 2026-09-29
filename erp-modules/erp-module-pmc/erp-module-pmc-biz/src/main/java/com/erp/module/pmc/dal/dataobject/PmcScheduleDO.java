package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 排产结果（pmc_schedule） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_schedule")
public class PmcScheduleDO extends BaseDO {

    private Long prodOrderId;
    private String prodOrderNo;
    private Long materialId;
    private Integer operationSeq;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operation;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long workCenterId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deptId;
    private LocalDateTime schedStart;
    private LocalDateTime schedEnd;
    private BigDecimal loadHours;
    /** 按日占用：yyyy-MM-dd=小时;… */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String dayLoads;
    /** 需求日期 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate dueDate;
    private Boolean isLate;
    private Integer priority;
    private Boolean locked;
    private Boolean manual;
    private Boolean applied;
}
