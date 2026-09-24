package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 库存余额（物料 + 仓库 + 库位 + 批次） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_stock")
public class StockDO extends BaseDO {

    private Long materialId;
    private Long warehouseId;
    private Long locationId;
    private String batchNo;
    private BigDecimal qty;
    private LocalDate lastInDate;
    private LocalDate lastOutDate;
}
