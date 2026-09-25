package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/** 工序 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_routing_step")
public class RoutingStepDO extends BaseDO {

    private Long routingId;
    /** 工序号 10、20… */
    private Integer seq;
    /** 字典 eng_operation */
    private String operation;
    private Long workCenterId;
    /** 准备时间（分钟/批） */
    private BigDecimal setupMinutes;
    /** 标准工时（秒/件） */
    private BigDecimal runSeconds;
    /** 报工点 */
    private Boolean isReportPoint;
    /** 检验点 */
    private Boolean isInspectionPoint;
    /** 委外工序 */
    private Boolean isOutsourced;
    /** 作业要点 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
