package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 缺料分析快照（订单）（pmc_shortage_order） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_shortage_order")
public class PmcShortageOrderDO extends BaseDO {

    private String snapshotNo;
    private Long prodOrderId;
    private String prodOrderNo;
    private Long productId;
    private BigDecimal qty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate planStart;
    private Integer priority;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String prodStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deptId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String salesOrderNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate customerDate;
    private Integer lineCount;
    private Integer shortLineCount;
    private BigDecimal lineKitRate;
    private BigDecimal qtyKitRate;
    private BigDecimal kitableQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate etaDate;
    private Boolean hasNoSupply;
    private Integer sortNo;
}
