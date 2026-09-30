package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 库存日快照（bi_agg_inventory_daily_snapshot） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_agg_inventory_daily_snapshot")
public class BiAggInventorySnapshotDO extends BaseDO {

    private LocalDate statDate;
    private String period;
    private Long warehouseId;
    private String warehouseType;
    private Long materialId;
    private Long categoryId;
    private BigDecimal qty;
    private BigDecimal amount;
    private LocalDate lastOutDate;
    private LocalDate lastInDate;
    /** 截至快照日无出库天数（从无出库按最近入库；都没有为空） */
    private Integer idleDays;
    /** 截至快照日距最近入库天数 */
    private Integer ageDays;
}
