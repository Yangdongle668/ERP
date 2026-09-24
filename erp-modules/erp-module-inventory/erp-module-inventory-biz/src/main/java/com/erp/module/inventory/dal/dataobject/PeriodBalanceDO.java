package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 期末结存 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_period_balance")
public class PeriodBalanceDO extends BaseDO {

    private String period;
    private Long materialId;
    private Long warehouseId;
    private BigDecimal qty;
    private BigDecimal amount;
    private BigDecimal avgCost;
}
