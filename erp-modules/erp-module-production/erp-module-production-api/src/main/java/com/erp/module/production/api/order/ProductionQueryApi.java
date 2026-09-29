package com.erp.module.production.api.order;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 生产查询（PMC、研发工程、销售、财务） */
public interface ProductionQueryApi {

    /** 在制数量（按物料），没有在制的物料不出现在结果中 */
    Map<Long, WipDTO> getWipQty(Collection<Long> materialIds);

    /** 使用该子件的已下达未关闭订单 */
    List<ComponentDemandDTO> getOpenOrdersByComponent(Long componentId);

    List<ProgressDTO> getProgress(Collection<Long> prodOrderIds);

    /** 按销售订单行查询生产订单进度（MTO） */
    List<ProgressDTO> getProgressBySalesOrderLines(Collection<Long> salesOrderLineIds);

    /** BOM 版本是否被生产订单使用 */
    boolean isBomUsed(Long bomId);

    /** 已分配量：已下达未关闭订单的未领数量合计（基本单位） */
    BigDecimal getAllocatedQty(Long materialId);

    Map<Long, BigDecimal> getAllocatedQty(Collection<Long> materialIds);
}
