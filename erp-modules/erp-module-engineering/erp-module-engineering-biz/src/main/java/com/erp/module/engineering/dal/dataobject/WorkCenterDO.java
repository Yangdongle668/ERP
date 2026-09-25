package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/** 工作中心 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_work_center")
public class WorkCenterDO extends BaseDO {

    private String code;
    private String name;
    /** 所属车间 */
    private Long deptId;
    /** LINE/MACHINE/MANUAL/OUTSOURCE */
    private String wcType;
    /** 每班小时数 */
    private BigDecimal hoursPerShift;
    /** 班次数 */
    private Integer shiftCount;
    /** 效率，1 = 100% */
    private BigDecimal efficiencyPct;
    /** 人工费率（本位币/小时） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal laborRate;
    /** 制费费率（本位币/小时） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal overheadRate;
    /** ENABLED/DISABLED */
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
