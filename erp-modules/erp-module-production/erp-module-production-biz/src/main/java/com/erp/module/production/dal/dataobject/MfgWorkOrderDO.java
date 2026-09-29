package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 工单（派工）（mfg_work_order） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "mfg_work_order", autoResultMap = true)
public class MfgWorkOrderDO extends BaseDocDO {

    private Long prodOrderId;
    private Integer operationSeq;
    private Long workCenterId;
    private LocalDate planDate;
    private String shift;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long teamLeaderId;
    private BigDecimal planQty;
    private BigDecimal goodQty;
    private BigDecimal defectQty;
    private BigDecimal scrapQty;
    /** DISPATCHED/RUNNING/DONE/CANCELED */
    private String woStatus;
}
