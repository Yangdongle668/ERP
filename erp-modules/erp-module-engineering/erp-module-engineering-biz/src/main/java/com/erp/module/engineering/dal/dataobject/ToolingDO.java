package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 工装台账 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_tooling")
public class ToolingDO extends BaseDO {

    /** 工装编号 */
    private String code;
    private String name;
    /** 字典 eng_tooling_type */
    private String toolingType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String spec;
    /** OWN/CUSTOMER */
    private String ownership;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    /** 模穴数 */
    private Integer cavity;
    /** 设计寿命（次） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer designLife;
    /** 已使用次数 */
    private Integer usedCount;
    /** 保养周期（次） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer maintainCycle;
    /** 上次保养时的使用次数 */
    private Integer lastMaintainCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String location;
    /** IN_STOCK/IN_USE/LENT/REPAIRING/SCRAPPED */
    private String toolingStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long holderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate purchaseDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal purchaseAmount;
    /** 超寿命继续使用 */
    private Boolean allowOverLife;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String overLifeReason;
    /** 已发寿命预警 */
    private Boolean lifeWarned;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
