package com.erp.module.purchase.api.order;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/** 采购在途查询（PMC、仓库、研发工程 ECN 使用）。 */
public interface PurchaseQueryApi {

    /** 在途数量（已审核未到货，基本单位，含预计到货日期明细）；没有在途的物料不出现在结果中 */
    Map<Long, InTransitDTO> getInTransitQty(Collection<Long> materialIds);

    /** 某物料未完成采购订单的未到货数量合计（基本单位） */
    BigDecimal getOpenQtyByMaterial(Long materialId);
}
