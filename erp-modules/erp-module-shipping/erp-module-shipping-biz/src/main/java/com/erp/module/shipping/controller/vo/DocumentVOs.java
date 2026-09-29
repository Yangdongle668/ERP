package com.erp.module.shipping.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 出货单证（11-04） */
public final class DocumentVOs {

    private DocumentVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DocQuery extends PageParam {
        private String no;
        private String shipmentNo;
        private Long customerId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private Boolean invalid;
    }

    public record DocRow(Long id, String no, LocalDate date, Long shipmentId, String shipmentNo, Long customerId, String customerName, String currency,
                         BigDecimal totalQty, Integer cartons, BigDecimal totalAmount, boolean invalid, LocalDateTime createdAt) {
    }

    // ==================== Packing List ====================

    /** PL 行快照 */
    public record PlLine(String cartonRange, String description, String partNo, String batchNo, BigDecimal qtyPerCarton, Integer cartons, BigDecimal qty,
                         String uom, BigDecimal netWeight, BigDecimal grossWeight, BigDecimal cbm) {
    }

    public record PlTotals(Integer cartons, BigDecimal qty, BigDecimal netWeight, BigDecimal grossWeight, BigDecimal cbm) {
    }

    public record PackingListDetail(Long id, String plNo, LocalDate plDate, Long shipmentId, String shipmentNo, String shipmentStatus, Long customerId,
                                    String customerName, String consignee, String notifyParty, String shippingMarks, List<PlLine> lines, PlTotals totals,
                                    String remark, boolean invalid, LocalDateTime createdAt) {
    }

    public record PlLineEdit(String description, String partNo) {
    }

    /** 可修改：单号、日期、收货人、通知方、唛头、描述、料号、备注；数量与重量只读 */
    public record PackingListSave(@NotBlank String plNo, @NotNull LocalDate plDate, String consignee, String notifyParty, String shippingMarks,
                                  String remark, List<PlLineEdit> lines) {
    }

    // ==================== Invoice ====================

    public record InvoiceLineVO(Long id, int lineNo, Long shipmentLineId, String orderNo, String customerPoNo, String customerPartNo, String description,
                                String hsCode, String origin, BigDecimal qty, String uom, BigDecimal unitPrice, BigDecimal amount) {
    }

    public record InvoiceDetail(Long id, String invoiceNo, LocalDate invoiceDate, Long shipmentId, String shipmentNo, String shipmentStatus, Long customerId,
                                String customerName, String billTo, String consignee, String notifyParty, String currency, String tradeTerm,
                                String paymentTermText, String portOfLoading, String portOfDestination, String vesselFlight, BigDecimal totalAmount,
                                String amountInWords, String bankInfo, String remark, boolean invalid, boolean priceVisible, List<InvoiceLineVO> lines,
                                LocalDateTime createdAt) {
    }

    public record InvoiceLineEdit(@NotNull Long id, String customerPoNo, String customerPartNo, String description, String hsCode, String origin) {
    }

    /** SHP-DOC-R01：金额、数量不可修改，只能改描述性字段 */
    public record InvoiceSave(@NotBlank String invoiceNo, @NotNull LocalDate invoiceDate, String billTo, String consignee, String notifyParty,
                              String tradeTerm, String paymentTermText, String portOfLoading, String portOfDestination, String vesselFlight, String bankInfo,
                              String remark, @Valid List<InvoiceLineEdit> lines) {
    }

    // ==================== 报关资料 ====================

    public record CustomsItemVO(Long id, int seq, String hsCode, String declareName, String declareElements, BigDecimal qty, String uom, BigDecimal secondQty,
                                String secondUom, BigDecimal unitPrice, BigDecimal amount, String origin, BigDecimal netWeight, BigDecimal grossWeight) {
    }

    public record CustomsDetail(Long id, String docCode, Long shipmentId, String shipmentNo, String shipmentStatus, Long customerId, String customerName,
                                String customsNo, LocalDate declareDate, String tradeMode, String declarePort, String destinationCountry, String currency,
                                BigDecimal totalAmount, String remark, boolean invalid, List<CustomsItemVO> items, LocalDateTime createdAt) {
    }

    public record CustomsItemEdit(@NotNull Long id, @NotBlank String hsCode, @NotBlank String declareName, String declareElements, String uom,
                                  BigDecimal secondQty, String secondUom, String origin) {
    }

    public record CustomsSave(String customsNo, LocalDate declareDate, String tradeMode, String declarePort, String destinationCountry, String remark,
                              @Valid List<CustomsItemEdit> items, List<Long> fileIds) {
    }
}
