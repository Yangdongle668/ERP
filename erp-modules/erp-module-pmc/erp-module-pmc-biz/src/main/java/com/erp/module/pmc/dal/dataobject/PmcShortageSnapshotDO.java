package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 缺料分析快照（用料行）（pmc_shortage_snapshot） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_shortage_snapshot")
public class PmcShortageSnapshotDO extends BaseDO {

    private String snapshotNo;
    private Long prodOrderId;
    private Long componentId;
    /** 计划开工 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate needDate;
    private BigDecimal unissuedQty;
    private BigDecimal allocatedQty;
    private BigDecimal shortageQty;
    /** 覆盖缺料的在途 JSON */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplyDetail;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate etaDate;
    private BigDecimal noSupplyQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long buyerId;
}
