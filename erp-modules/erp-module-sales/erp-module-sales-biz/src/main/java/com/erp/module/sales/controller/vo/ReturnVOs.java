package com.erp.module.sales.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.sales.controller.vo.CommonVOs.RelatedDoc;
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

/** 销售退货（需求 04-06） */
public final class ReturnVOs {

    private ReturnVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ReturnQuery extends PageParam {
        private String docNo;
        private Long customerId;
        private String rmaNo;
        private String returnReason;
        private String handling;
        private String statuses;
        private Long materialId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    /**
     * @param receiveStatus NONE 未收货 / PARTIAL 部分 / DONE 已收齐
     * @param judgeStatus   NONE 未判定 / PARTIAL 部分 / DONE 已判定
     */
    public record ReturnRow(Long id, String docNo, Long customerId, String customerName, String rmaNo, String returnReason, String handling,
                            String materialSummary, BigDecimal totalQty, String currency, BigDecimal totalAmount, String receiveStatus,
                            String judgeStatus, String status, Long ownerId, String ownerName, LocalDate docDate) {
    }

    /** @param qty 退货数量（基本单位） */
    public record ReturnLineSave(@NotNull(message = "请选择原订单行") Long orderLineId, Long shipmentLineId, @Size(max = 64) String batchNo,
                                 String serialNos, @NotNull(message = "请填写退货数量") BigDecimal qty, @Size(max = 256) String remark) {
    }

    public record ReturnSave(@NotNull(message = "请选择客户") Long customerId, @Size(max = 64) String rmaNo,
                             @NotBlank(message = "请选择退货原因") String returnReason, @NotBlank(message = "请选择处理方式") String handling,
                             @Size(max = 64) String complaintNo, LocalDate expectedArrivalDate, @Size(max = 1000) String remark,
                             @Valid List<ReturnLineSave> lines, List<Long> fileIds, Integer version) {
    }

    public record ReturnLineResp(Long id, int lineNo, Long orderId, String orderNo, Long orderLineId, Integer orderLineNo, Long materialId,
                                 String materialCode, String materialName, String materialSpec, String baseUom, String batchNo, String serialNos,
                                 BigDecimal qty, BigDecimal returnableQty, BigDecimal priceInclTax, BigDecimal taxRate, BigDecimal totalAmount,
                                 BigDecimal receivedQty, BigDecimal goodQty, BigDecimal reworkQty, BigDecimal scrapQty, String remark) {
    }

    public record ReturnDetail(Long id, String docNo, LocalDate docDate, String status, Long customerId, String customerName, String rmaNo,
                               String returnReason, String handling, String currency, BigDecimal exchangeRate, BigDecimal totalAmount,
                               BigDecimal totalAmountBase, String complaintNo, Long stockInId, LocalDate expectedArrivalDate, String voidReason,
                               String remark, Long ownerId, String ownerName, LocalDateTime createdAt, int version, List<ReturnLineResp> lines,
                               List<RelatedDoc> related) {
    }

    /** 退货选单：客户已出货的订单行 */
    public record ShippedLine(Long orderLineId, Long orderId, String orderNo, int lineNo, Long materialId, String materialCode, String materialName,
                              String materialSpec, String baseUom, BigDecimal shippedQty, BigDecimal returnedQty, BigDecimal returnableQty,
                              String currency, BigDecimal priceInclTax, LocalDate lastShipDate) {
    }

    /** 判定结果（基本单位，覆盖该行的判定数量） */
    public record JudgeReq(@NotNull Long lineId, BigDecimal goodQty, BigDecimal reworkQty, BigDecimal scrapQty) {
    }
}
