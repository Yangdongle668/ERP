package com.erp.module.shipping.api.query;

import java.time.LocalDate;
import java.util.List;

/** 出货查询（销售退货选单、生产 / 品质追溯、财务） */
public interface ShipmentQueryApi {

    /**
     * 客户已出货的行（退货选单），按出货日期倒序。
     *
     * @param materialId 可空
     * @param from       出货日期起，可空
     */
    List<ShippedLineDTO> getShippedLines(Long customerId, Long materialId, LocalDate from);

    /** 某物料批次的出货记录（追溯、召回） */
    List<ShippedLineDTO> getShipmentsByBatch(Long materialId, String batchNo);

    /** 订单的出货记录 */
    List<ShippedLineDTO> getShipmentsByOrder(Long orderId);
}
