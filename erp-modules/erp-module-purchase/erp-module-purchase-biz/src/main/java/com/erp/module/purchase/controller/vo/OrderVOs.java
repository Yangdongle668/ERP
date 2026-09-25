package com.erp.module.purchase.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.purchase.controller.vo.CommonVOs.RelatedDoc;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 采购订单与订单变更（需求 07-05） */
public final class OrderVOs {

    private OrderVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class OrderQuery extends PageParam {
        private String docNo;
        private Long supplierId;
        /** 采购员 */
        private Long ownerId;
        private String statuses;
        private Long materialId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private LocalDate requiredFrom;
        private LocalDate requiredTo;
        private String orderType;
        /** 只看逾期：有未到货行且确认交期（没有时要求日期）早于今天 */
        private Boolean overdue;
        private String columns;
    }

    public record OrderRow(Long id, String docNo, LocalDate docDate, String orderType, Long supplierId, String supplierName, Long ownerId,
                           String ownerName, String currency, BigDecimal totalAmount, LocalDate earliestDate, BigDecimal orderedQty,
                           BigDecimal receivedQty, int overdueLines, int delayedLines, LocalDateTime sentAt, int orderVersion, boolean hasPriceOverrun,
                           String status) {
    }

    /** price / priceInclTax 按单头“单价含税”开关取其一作为录入值 */
    public record OrderLineSave(Long id, @NotNull(message = "请选择物料") Long materialId, String uom, BigDecimal qty, BigDecimal price,
                                BigDecimal priceInclTax, BigDecimal taxRate, LocalDate requiredDate, Long requisitionLineId,
                                @Size(max = 256) String remark) {
    }

    public record OrderSave(String orderType, @NotNull(message = "请选择供应商") Long supplierId, Long supplierContactId, String currency,
                            BigDecimal exchangeRate, Long paymentTermId, String tradeTerm, Boolean taxIncluded, @Size(max = 256) String deliveryAddress,
                            Long ownerId, @Size(max = 1000) String remark, @Valid List<OrderLineSave> lines, List<Long> fileIds, Integer version) {
    }

    public record OrderLineResp(Long id, Integer lineNo, Long materialId, String materialCode, String materialName, String materialNameEn,
                                String materialSpec, String baseUom, String supplierPartNo, String uom, BigDecimal qty, BigDecimal baseQty,
                                BigDecimal price, BigDecimal priceInclTax, BigDecimal taxRate, BigDecimal amount, BigDecimal taxAmount,
                                BigDecimal totalAmount, BigDecimal listPrice, boolean priceOverrun, LocalDate requiredDate, LocalDate confirmedDate,
                                BigDecimal receivedQty, BigDecimal stockedQty, BigDecimal qualifiedQty, BigDecimal returnedQty, BigDecimal replaceQty,
                                BigDecimal statementQty, BigDecimal openQty, LocalDate firstReceivedDate, Long requisitionLineId, String requisitionNo,
                                Long requisitionId, String lineStatus, boolean delayed, boolean overdue, String lastFollowUp, LocalDateTime followUpAt,
                                String remark) {
    }

    public record OrderDetail(Long id, String docNo, LocalDate docDate, String status, String orderType, Long supplierId, String supplierCode,
                              String supplierName, String supplierLevel, Long supplierContactId, String contactName, String currency,
                              BigDecimal exchangeRate, Long paymentTermId, String paymentTermName, String tradeTerm, boolean taxIncluded,
                              String deliveryAddress, BigDecimal amount, BigDecimal taxAmount, BigDecimal totalAmount, BigDecimal totalAmountBase,
                              int orderVersion, boolean hasPriceOverrun, LocalDateTime sentAt, String closeReason, Long ownerId, String ownerName,
                              String remark, boolean priceVisible, boolean hasReceipt, Long runningChangeId, String createdByName,
                              LocalDateTime createdAt, Integer version, List<OrderLineResp> lines, List<RelatedDoc> related) {
    }

    /** 保存结果：订单 ID + 提示（无有效价格、MOQ/MPQ） */
    public record OrderSaveResult(Long id, List<String> warnings) {
    }

    public record FromRequisitionLine(@NotNull Long requisitionLineId, Long supplierId, BigDecimal qty) {
    }

    public record FromRequisitionReq(@Valid List<FromRequisitionLine> lines) {
    }

    /** 从申请生成：每个供应商一张草稿订单 */
    public record FromRequisitionResult(List<Long> orderIds, List<String> messages) {
    }

    public record ConfirmDate(@NotNull Long lineId, LocalDate confirmedDate) {
    }

    public record ConfirmDatesReq(@Valid List<ConfirmDate> lines) {
    }

    /** 到货选单：已审核/执行中订单未关闭、未到货数量 > 0 的行 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class OpenLineQuery extends PageParam {
        private Long supplierId;
        private Long materialId;
        private String orderNo;
        /** STANDARD / SAMPLE */
        private String orderType;
    }

    /** @param openQty 未到货（订单单位）；openBaseQty 未到货（基本单位） */
    public record OpenLine(Long id, Long orderId, String orderNo, Integer lineNo, String orderType, Long supplierId, Long materialId,
                           String materialCode, String materialName, String materialSpec, String baseUom, String uom, BigDecimal qty,
                           BigDecimal receivedQty, BigDecimal openQty, BigDecimal openBaseQty, LocalDate requiredDate, LocalDate confirmedDate,
                           boolean inspectRequired, Integer shelfLifeDays) {
    }

    // ==================== 变更 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChangeQuery extends PageParam {
        private Long orderId;
        private String docNo;
        private String statuses;
    }

    public record ChangeRow(Long id, String docNo, LocalDate docDate, Long orderId, String orderNo, String changeReason, int newVersion,
                            BigDecimal amountChangeBase, String status, String ownerName, LocalDateTime createdAt) {
    }

    /** ADD 新增行（orderLineId 为空）/ MODIFY 修改 / CANCEL 取消；newPrice 为不含税单价 */
    public record ChangeLineSave(Long orderLineId, @NotBlank String changeType, Long materialId, String uom, BigDecimal newQty,
                                 BigDecimal newPrice, BigDecimal taxRate, LocalDate newRequiredDate, @Size(max = 256) String remark) {
    }

    public record ChangeSave(@NotNull(message = "请选择采购订单") Long orderId, @NotBlank(message = "请填写变更原因") @Size(max = 512) String changeReason,
                             @Valid List<ChangeLineSave> lines, List<Long> fileIds, Integer version) {
    }

    public record ChangeLineResp(Long id, Integer lineNo, Long orderLineId, Integer orderLineNo, String changeType, Long materialId, String materialCode,
                                 String materialName, String uom, BigDecimal oldQty, BigDecimal newQty, BigDecimal oldPrice, BigDecimal newPrice,
                                 BigDecimal taxRate, LocalDate oldRequiredDate, LocalDate newRequiredDate, BigDecimal receivedQty, String remark) {
    }

    public record ChangeDetail(Long id, String docNo, LocalDate docDate, String status, Long orderId, String orderNo, int orderVersion,
                               String changeReason, int newVersion, BigDecimal amountChangeBase, String ownerName, LocalDateTime createdAt,
                               Integer version, boolean priceVisible, List<ChangeLineResp> lines) {
    }
}
