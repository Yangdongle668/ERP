package com.erp.module.purchase.api.order;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 采购在途查询（PMC、仓库、研发工程 ECN 使用）。 */
public interface PurchaseQueryApi {

    /** 在途数量（已审核未到货，基本单位，含预计到货日期明细）；没有在途的物料不出现在结果中 */
    Map<Long, InTransitDTO> getInTransitQty(Collection<Long> materialIds);

    /** 某物料未完成采购订单的未到货数量合计（基本单位） */
    BigDecimal getOpenQtyByMaterial(Long materialId);

    /** 采购订单头（财务预付款） */
    Optional<PurchaseOrderHeaderDTO> getOrderHeader(Long orderId);

    /** 供应商已审核 / 执行中的采购订单（预付款选单），按单据日期倒序 */
    List<PurchaseOrderHeaderDTO> getOpenOrders(Long supplierId);
}
