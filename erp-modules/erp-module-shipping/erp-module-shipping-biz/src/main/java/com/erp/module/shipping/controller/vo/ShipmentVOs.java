package com.erp.module.shipping.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.shipping.controller.vo.PackingVOs.CartonVO;
import com.erp.module.shipping.controller.vo.PackingVOs.PackingTotals;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 出货单（11-03）与物流（11-05） */
public final class ShipmentVOs {

    private ShipmentVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ShipmentQuery extends PageParam {
        private String docNo;
        private Long customerId;
        private String orderNo;
        private String noticeNo;
        private String blNo;
        /** 逗号分隔；UNSIGNED = 已出货未完成 */
        private String statuses;
        private LocalDate shipDateFrom;
        private LocalDate shipDateTo;
        private String transportMode;
        /** 逗号分隔；NONE = 未登记 */
        private String logisticsStatuses;
        private Long forwarderId;
        private LocalDate etaFrom;
        private LocalDate etaTo;
    }

    public record ShipmentRow(Long id, String docNo, Long noticeId, String noticeNo, Long customerId, String customerName, LocalDate shipDate,
                              String transportMode, String portOfDestination, BigDecimal totalQty, String currency, BigDecimal totalAmount,
                              Integer cartonCount, String blNo, LocalDate etd, LocalDate eta, boolean etaOverdue, Long forwarderId, String forwarderName,
                              String containerNo, String logisticsStatus, LocalDateTime logisticsUpdatedAt, String shipmentStatus, Long ownerId,
                              String ownerName, LocalDateTime createdAt) {
    }

    /** 从出货通知生成：cartonIds 为空时取全部未出货箱；未启用装箱时可用 units 指定数量 */
    public record GenerateReq(List<Long> cartonIds, List<UnitQty> units, LocalDate shipDate) {
    }

    public record UnitQty(@NotNull Long noticeLineId, String batchNo, @NotNull BigDecimal qty) {
    }

    public record ShipmentSave(@NotNull LocalDate shipDate, Long forwarderId, String containerNo, String sealNo, String remark, List<Long> fileIds) {
    }

    public record ShipmentLineVO(Long id, int lineNo, Long noticeLineId, Long orderId, String orderNo, Long orderLineId, String customerPoNo, Long materialId,
                                 String materialCode, String materialName, String spec, String customerPartNo, String description, String uom, BigDecimal qty,
                                 BigDecimal baseQty, String baseUom, String batchNo, String serialNos, BigDecimal priceInclTax, BigDecimal taxRate,
                                 BigDecimal amount, BigDecimal taxAmount, BigDecimal totalAmount, BigDecimal outQty) {
    }

    public record LogisticsEventVO(Long id, String logisticsStatus, LocalDateTime occurredAt, String location, String remark, Long operatorId,
                                   String operatorName) {
    }

    public record DocRef(Long id, String no, boolean invalid) {
    }

    public record ShipmentDetail(Long id, String docNo, LocalDate docDate, String status, String shipmentStatus, Long noticeId, String noticeNo,
                                 Long customerId, String customerName, boolean foreignCustomer, String currency, BigDecimal exchangeRate, LocalDate shipDate,
                                 String transportMode, String tradeTerm, String portOfLoading, String portOfDestination, String shipToText, Long warehouseId,
                                 String warehouseName, BigDecimal totalQty, BigDecimal totalAmount, BigDecimal totalAmountBase, Integer cartonCount,
                                 BigDecimal grossWeight, BigDecimal netWeight, BigDecimal cbm, Long stockOutId, DocRef packingList, DocRef invoice,
                                 DocRef customs, Long forwarderId, String forwarderName, String containerNo, String sealNo, String blNo, LocalDate blDate,
                                 LocalDate etd, LocalDate eta, String logisticsStatus, LocalDateTime logisticsUpdatedAt, LocalDateTime signedAt,
                                 String signedBy, boolean creditWarning, boolean prepaymentUnpaid, LocalDateTime shippedAt, String voidReason, Long ownerId,
                                 String ownerName, String remark, List<ShipmentLineVO> lines, List<CartonVO> cartons, PackingTotals cartonTotals,
                                 List<LogisticsEventVO> events, Long createdBy, String createdByName, LocalDateTime createdAt, boolean priceVisible) {
    }

    /** 登记物流：提单号、提单日期、ETD、ETA、货代、柜号 */
    public record LogisticsReq(String blNo, LocalDate blDate, LocalDate etd, LocalDate eta, Long forwarderId, String containerNo, String sealNo) {
    }

    public record SignReq(@NotNull LocalDateTime signedAt, String signedBy, String remark, List<Long> fileIds) {
    }

    public record LogisticsEventReq(@NotBlank String logisticsStatus, @NotNull LocalDateTime occurredAt, String location, String remark) {
    }

    public record SubmitResult(Long id, List<String> warnings) {
    }
}
