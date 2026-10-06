package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 拆解订单产出（mfg_prod_order_output）：下达时按 BOM 固化 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mfg_prod_order_output")
public class MfgProdOrderOutputDO extends BaseDO {

    private Long prodOrderId;
    private Integer lineNo;
    private Long componentId;
    /** 每拆解 1 个产品产出的子件数量（已除以 BOM 基数） */
    private BigDecimal qtyPer;
    private BigDecimal expectedQty;
    private BigDecimal receivedQty;
}
