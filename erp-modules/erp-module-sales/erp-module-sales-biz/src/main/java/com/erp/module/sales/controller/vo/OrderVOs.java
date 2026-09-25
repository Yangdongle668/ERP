package com.erp.module.sales.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.sales.controller.vo.CommonVOs.RelatedDoc;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 销售订单（需求 04-03）、回款计划（04-07） */
public final class OrderVOs {

    private OrderVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class OrderQuery extends PageParam {
        /** 单号或客户 PO 号（前缀） */
        private String docNo;
        private Long customerId;
        /** 逗号分隔；为空默认草稿、待审批、已审核、执行中 */
        private String statuses;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private Long ownerId;
        private Long materialId;
        private String orderType;
        private LocalDate requiredFrom;
        private LocalDate requiredTo;
        private Boolean deliveryRisk;
        /** 只看未出完 */
        private Boolean unshipped;
        private String columns;
    }

    /**
     * @param shipProgress    已出货金额 ÷ 价税合计（0～1）
     * @param receiveProgress 已回款 ÷ 价税合计（0～1）
     */
    public record OrderRow(Long id, String docNo, int orderVersion, String orderType, Long customerId, String customerName, String customerPoNo,
                           String currency, BigDecimal totalAmount, BigDecimal minMarginRate, boolean belowFloor, LocalDate earliestRequiredDate,
                           BigDecimal shipProgress, BigDecimal receiveProgress, boolean deliveryRisk, String status, Long ownerId, String ownerName,
                           LocalDate docDate, LocalDateTime createdAt) {
    }

    /** 订单明细导出 / 订单明细报表行 */
    public record OrderLineRow(Long orderId, String docNo, LocalDate docDate, String status, Long customerId, String customerName,
                               String customerPoNo, String ownerName, String currency, BigDecimal exchangeRate, int lineNo, Long lineId, Long materialId,
                               String materialCode, String materialName, String materialSpec, String customerPartNo, String uom, BigDecimal qty,
                               BigDecimal baseQty, BigDecimal price, BigDecimal totalAmount, BigDecimal totalAmountBase, LocalDate requiredDate,
                               LocalDate promisedDate, BigDecimal shippedQty, BigDecimal openQty, BigDecimal invoicedQty, String lineStatus,
                               BigDecimal marginRate) {
    }

    /**
     * @param price 按单头“价格含税”录入的单价（每销售单位）；为空时自动取价
     */
    public record OrderLineSave(Long materialId, @Size(max = 64) String customerPartNo, @Size(max = 512) String description, String uom,
                                BigDecimal qty, BigDecimal price, BigDecimal taxRate, LocalDate requiredDate, Long quotationLineId,
                                @Size(max = 64) String priceSource, @Size(max = 256) String remark) {
    }

    public record OrderSave(@Size(max = 64) String docNo, String orderType, @NotNull(message = "请选择客户") Long customerId, Long contactId,
                            @Size(max = 64) String customerPoNo, LocalDate customerPoDate, LocalDate docDate, Long ownerId, String currency,
                            BigDecimal exchangeRate, Boolean taxIncluded, Long paymentTermId, String tradeTerm, @Size(max = 64) String portOfLoading,
                            @Size(max = 64) String portOfDestination, Long shipToAddressId, Long billToAddressId, @Size(max = 2000) String terms,
                            @Size(max = 1000) String remark, @Valid List<OrderLineSave> lines, List<Long> fileIds, Integer version) {
    }

    /** 选择客户后带出的默认值（SAL-SO-T01） */
    public record CustomerDefaults(Long customerId, String customerName, String customerStatus, boolean foreign, String currency, BigDecimal exchangeRate,
                                   boolean taxIncluded, BigDecimal salesTaxRate, Long paymentTermId, String tradeTerm, Long ownerId, String ownerName,
                                   Long shipToAddressId, Long billToAddressId, Long contactId, List<AddressOption> addresses,
                                   List<ContactOption> contacts) {
    }

    public record AddressOption(Long id, String type, String text, boolean isDefault) {
    }

    public record ContactOption(Long id, String name, String title, String email, String phone, boolean primary) {
    }

    public record OrderLineResp(Long id, int lineNo, Long materialId, String materialCode, String materialName, String materialSpec, String baseUom,
                                String customerPartNo, String description, String uom, BigDecimal qty, BigDecimal baseQty, BigDecimal price,
                                BigDecimal priceInclTax, BigDecimal taxRate, BigDecimal amount, BigDecimal taxAmount, BigDecimal totalAmount,
                                String priceSource, BigDecimal costPrice, BigDecimal marginRate, boolean belowFloor, LocalDate requiredDate,
                                LocalDate promisedDate, String promiseRemark, boolean delayed, BigDecimal noticedQty, BigDecimal shippedQty,
                                BigDecimal returnedQty, BigDecimal invoicedQty, BigDecimal openQty, String lineStatus, Long quotationLineId,
                                String remark) {
    }

    public record OrderDetail(Long id, String docNo, LocalDate docDate, String status, String orderType, Long customerId, String customerCode,
                              String customerName, String customerLevel, Long contactId, String contactName, String customerPoNo,
                              LocalDate customerPoDate, Long quotationId, String quotationNo, String currency, BigDecimal exchangeRate,
                              boolean taxIncluded, Long paymentTermId, String paymentTermName, String tradeTerm, String portOfLoading,
                              String portOfDestination, Long shipToAddressId, String shipToText, Long billToAddressId, String billToText,
                              BigDecimal amount, BigDecimal taxAmount, BigDecimal totalAmount, BigDecimal totalAmountBase, BigDecimal shippedAmount,
                              BigDecimal receivedAmount, BigDecimal minMarginRate, boolean belowFloor, boolean creditWarning, int orderVersion,
                              boolean deliveryRisk, int riskLineCount, String closeReason, String terms, String remark, Long ownerId,
                              String ownerName, LocalDateTime approvedAt, boolean costVisible, Long runningChangeId, String runningChangeNo,
                              String createdByName, LocalDateTime createdAt, int version, List<OrderLineResp> lines, List<RelatedDoc> related) {
    }

    /** 提交：confirmCredit 为 true 表示已确认信用警告 */
    public record SubmitReq(Boolean confirmCredit) {
    }

    /** 从报价生成：报价单行 + 下单数量（销售单位） */
    public record FromQuotationLine(@NotNull Long quotationLineId, @NotNull BigDecimal qty, LocalDate requiredDate) {
    }

    public record FromQuotationResult(List<Long> orderIds, List<String> messages) {
    }

    /** 执行情况：每行的通知 / 出货 / 开票等流水 */
    public record ExecRow(Long id, Long orderLineId, Integer lineNo, String execType, String docType, String docNo, BigDecimal qty, BigDecimal amount,
                         LocalDate execDate, LocalDateTime createdAt) {
    }

    public record SnapshotRow(Long id, int orderVersion, Long changeId, String changeNo, String content, LocalDateTime createdAt) {
    }

    /** 出货选单：未出完订单行 */
    public record OpenLineRow(Long lineId, Long orderId, String orderNo, int lineNo, Long customerId, String customerName, Long materialId,
                              String materialCode, String materialName, String materialSpec, String uom, BigDecimal qty, BigDecimal baseQty,
                              BigDecimal shippedQty, BigDecimal openQty, BigDecimal noticeableQty, LocalDate requiredDate, LocalDate promisedDate,
                              String currency, BigDecimal priceInclTax) {
    }

    // ==================== 回款计划 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaymentPlanQuery extends PageParam {
        private Long customerId;
        private String orderNo;
        private Long ownerId;
        /** 逗号分隔；为空默认未收齐（NOT_DUE、DUE、OVERDUE、PARTIAL） */
        private String statuses;
        private LocalDate dueFrom;
        private LocalDate dueTo;
        private Boolean overdueOnly;
        private String columns;
    }

    public record PaymentPlanRow(Long id, Long orderId, String orderNo, Long customerId, String customerName, Long ownerId, String ownerName,
                                 int seq, int batchNo, String nodeName, BigDecimal percent, String baseEvent, int days, String currency,
                                 BigDecimal planAmount, LocalDate eventDate, LocalDate dueDate, int overdueDays, BigDecimal receivedAmount,
                                 BigDecimal unreceivedAmount, String planStatus, String remark, LocalDate promisedPayDate, LocalDateTime followedAt) {
    }

    /** 顶部汇总（本位币）：本月到期、已逾期、本月已收 */
    public record PaymentSummary(BigDecimal dueThisMonth, BigDecimal overdue, BigDecimal receivedThisMonth, String baseCurrency) {
    }

    /** 订单详情页签汇总（原币）：价税合计、已收、未收、预收（尚未出货但已收） */
    public record OrderPaymentSummary(BigDecimal totalAmount, BigDecimal receivedAmount, BigDecimal unreceivedAmount, BigDecimal advanceAmount,
                                      List<PaymentPlanRow> plans) {
    }

    public record FollowUpReq(@Size(max = 256) String remark, LocalDate promisedPayDate) {
    }
}
