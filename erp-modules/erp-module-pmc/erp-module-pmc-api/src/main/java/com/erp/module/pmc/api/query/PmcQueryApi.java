package com.erp.module.pmc.api.query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** PMC 查询（生产、销售调用） */
public interface PmcQueryApi {

    /**
     * 单张生产订单的缺料（实时计算：可用库存按开工先后分配给更早的订单后的剩余可用），没有缺料返回空列表。
     */
    List<ShortageDTO> getShortage(Long prodOrderId);

    /** 销售订单行的预计可出货日期（最近一次交期预警计算结果；没有计算过时实时估算） */
    Optional<LocalDate> getEstimatedDate(Long orderLineId);
}
