package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 订单材料明细（fin_cost_order_material） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_cost_order_material")
public class FinCostOrderMaterialDO extends BaseDO {

    private Long costOrderId;
    private String period;
    private Long prodOrderId;
    private Long materialId;
    private BigDecimal issueQty;
    private BigDecimal returnQty;
    private BigDecimal unitCost;
    private BigDecimal amount;
}
