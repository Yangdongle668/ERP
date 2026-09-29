package com.erp.module.shipping.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 出货通知（11-01） */
public final class NoticeVOs {

    private NoticeVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class NoticeQuery extends PageParam {
        private String docNo;
        private Long customerId;
        private String orderNo;
        /** 逗号分隔；OPEN = 未出货未关闭 */
        private String statuses;
        private LocalDate shipDateFrom;
        private LocalDate shipDateTo;
        private String transportMode;
        private Long ownerId;
    }

    public record NoticeRow(Long id, String docNo, Long customerId, String customerName, LocalDate shipDate, boolean overdue, String transportMode,
                            String portOfDestination, int lineCount, BigDecimal totalQty, String currency, BigDecimal totalAmount, BigDecimal pickedQty,
                            BigDecimal packedQty, BigDecimal shippedQty, boolean oqcRequired, String oqcResult, String noticeStatus, Long ownerId,
                            String ownerName, String warehouseName, LocalDateTime createdAt) {
    }

    public record NoticeLineSave(Long id, @NotNull Long orderLineId, @NotNull BigDecimal qty, String description, String remark, Long shippingPlanLineId) {
    }

    public record NoticeSave(@NotNull Long customerId, @NotNull LocalDate shipDate, @NotBlank String transportMode, String tradeTerm, String portOfLoading,
                             String portOfDestination, @NotNull Long shipToAddressId, String notifyParty, Long forwarderId, @NotNull Long warehouseId,
                             Long ownerId, String remark, @NotEmpty @Valid List<NoticeLineSave> lines, List<Long> fileIds) {
    }

    /** 保存 / 提交结果：warnings 为不阻止的提示（R03 库存不足、R04 信用 / 出货前款项） */
    public record SaveResult(Long id, List<String> warnings) {
    }

    public record FromOrdersReq(@NotEmpty List<Long> orderLineIds, LocalDate shipDate, Long warehouseId, String transportMode) {
    }

    public record FromPlanReq(@NotBlank String planWeek, List<Long> planLineIds, Long warehouseId) {
    }

    public record NoticeLineVO(Long id, int lineNo, Long orderId, String orderNo, Long orderLineId, Integer orderLineNo, String customerPoNo, Long materialId,
                               String materialCode, String materialName, String spec, String customerPartNo, String description, String uom, BigDecimal qty,
                               BigDecimal baseQty, String baseUom, BigDecimal priceInclTax, BigDecimal taxRate, BigDecimal totalAmount, boolean oqcRequired,
                               BigDecimal pickedQty, BigDecimal packedQty, BigDecimal shippedQty, BigDecimal shortageQty, BigDecimal noticeableQty,
                               BigDecimal availableQty, Long shippingPlanLineId, String remark) {
    }

    public record LinkedDoc(Long id, String docNo, String status, LocalDate date, BigDecimal qty) {
    }

    public record NoticeDetail(Long id, String docNo, LocalDate docDate, String status, String noticeStatus, Long customerId, String customerName,
                               String currency, LocalDate shipDate, String transportMode, String tradeTerm, String portOfLoading, String portOfDestination,
                               Long shipToAddressId, String shipToText, String notifyParty, Long forwarderId, String forwarderName, Long warehouseId,
                               String warehouseName, boolean oqcRequired, String oqcResult, BigDecimal totalAmount, BigDecimal totalAmountBase,
                               boolean creditWarning, boolean prepaymentUnpaid, LocalDateTime approvedAt, String closeReason, Long ownerId, String ownerName,
                               String remark, boolean pickingEnabled, boolean packingEnabled, boolean pickingStarted, boolean pickingDone,
                               boolean canShip, List<NoticeLineVO> lines, List<LinkedDoc> pickings, List<LinkedDoc> shipments, Long createdBy,
                               String createdByName, LocalDateTime createdAt, boolean priceVisible) {
    }

    /** 新建时选择订单行（SourceDocPicker） */
    public record OrderLineOption(Long orderLineId, Long orderId, String orderNo, int lineNo, String customerPoNo, Long materialId, String materialCode,
                                  String materialName, String customerPartNo, String description, String uom, BigDecimal qty, BigDecimal noticedQty,
                                  BigDecimal noticeableQty, LocalDate promisedDate, BigDecimal availableQty, String currency, BigDecimal priceInclTax) {
    }

    public record AddressOption(Long id, String text, boolean isDefault) {
    }

    public record CustomerDefaults(Long customerId, String currency, String tradeTerm, Long warehouseId, List<AddressOption> addresses, Long shipToAddressId,
                                   String portOfLoading, String portOfDestination) {
    }

    public record PlanLineOption(Long planLineId, String planNo, String planWeek, Long customerId, String customerName, Long orderLineId, String orderNo,
                                 Integer lineNo, Long materialId, String materialCode, String materialName, BigDecimal planQty, BigDecimal noticedQty,
                                 LocalDate planShipDate, String transportMode) {
    }
}
