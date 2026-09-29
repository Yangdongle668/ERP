package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 生产订单工序（mfg_prod_order_operation） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mfg_prod_order_operation")
public class MfgProdOrderOperationDO extends BaseDO {

    private Long prodOrderId;
    private Integer seq;
    private String operation;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long workCenterId;
    private Boolean isReportPoint;
    private Boolean isInspectionPoint;
    private Boolean isOutsourced;
    private BigDecimal stdRunSeconds;
    private BigDecimal stdSetupMinutes;
    /** 累计合格（含返修合格） */
    private BigDecimal goodQty;
    /** 一次合格（不含返修） */
    private BigDecimal firstGoodQty;
    private BigDecimal defectQty;
    private BigDecimal scrapQty;
    private BigDecimal repairedQty;
    private BigDecimal dispatchedQty;
    private BigDecimal actualHours;
    /** WAITING/RUNNING/DONE */
    private String opStatus;
}
