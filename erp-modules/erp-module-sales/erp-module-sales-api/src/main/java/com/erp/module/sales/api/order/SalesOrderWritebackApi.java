package com.erp.module.sales.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 订单执行数据回写（SAL-SO-R11），由出货、财务模块在各自业务发生时调用（与调用方同一事务）。
 * 回写使用乐观锁并校验不超过允许数量；以 docType + docId 做幂等（同一出货单重复回写只生效一次）。
 */
public interface SalesOrderWritebackApi {

    /**
     * 出货通知保存 / 删除：调整已通知数量（基本单位，delta 可为负），不能超过可通知数量；
     * 首次通知时订单进入执行中。noticeNo 用于关闭订单时提示未完成的出货通知。
     */
    void onNoticeChanged(Long orderLineId, BigDecimal deltaBaseQty, Long noticeId, String noticeNo);

    /**
     * 出货确认：累加已出货数量，订单进入执行中 / 已完成，按本次出货金额拆分出货类回款计划，
     * 发布 {@link SalesOrderShipmentChangedEvent}、{@link SalesOrderOpenAmountChangedEvent}。
     */
    void onShipped(ShipmentRecord shipment);

    /** 出货冲销（出库反确认）：按原出货单扣回已出货数量与对应批次的回款计划 */
    void onShipmentReversed(Long shipmentId);

    /** 提单日期回填：计算该出货单对应的“提单日”类回款计划到期日 */
    void onBillOfLading(Long shipmentId, LocalDate blDate);

    /** 开票：累加已开票数量（delta 可为负，红字发票），并计算“开票日”类回款计划到期日 */
    void onInvoiced(Long orderLineId, BigDecimal deltaBaseQty, LocalDate invoiceDate);

    /** 收款分配（原币，负数为冲回）：按回款计划先到期先分配 */
    void onReceiptAllocated(Long orderId, BigDecimal amount, LocalDate receiptDate);

    /**
     * 出货单。
     *
     * @param shipDate 出货日期（出库日期）
     */
    record ShipmentRecord(Long shipmentId, String shipmentNo, LocalDate shipDate, List<Line> lines) {
    }

    /** @param baseQty 本次出货数量（基本单位） */
    record Line(Long orderLineId, BigDecimal baseQty) {
    }
}
