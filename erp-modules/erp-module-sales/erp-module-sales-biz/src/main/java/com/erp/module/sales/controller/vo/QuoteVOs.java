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
import java.util.Map;

/** RFQ、成本核算、报价单（需求 04-02） */
public final class QuoteVOs {

    private QuoteVOs() {
    }

    // ==================== RFQ ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RfqQuery extends PageParam {
        private String docNo;
        private Long customerId;
        private Long ownerId;
        private Long engineerId;
        /** rfqStatus，逗号分隔；为空默认非关闭 */
        private String statuses;
        private LocalDate replyFrom;
        private LocalDate replyTo;
    }

    /**
     * @param dueLevel    OVERDUE 已过期 / SOON 2 天内 / 空
     * @param feasibility ALL_OK / HAS_NG / PENDING
     */
    public record RfqRow(Long id, String docNo, Long customerId, String customerName, int lineCount, LocalDate replyDueDate, String dueLevel,
                         Long engineerId, String engineerName, Long costEngineerId, String costEngineerName, String feasibility, String rfqStatus,
                         Long ownerId, String ownerName, LocalDate docDate) {
    }

    public record RfqLineSave(@Size(max = 64) String customerPartNo, @NotBlank(message = "请填写需求描述") @Size(max = 512) String description,
                              Long materialId, BigDecimal annualQty, @NotBlank(message = "请填写数量阶梯") String qtyBreaks, BigDecimal targetPrice,
                              LocalDate requiredDate, @Size(max = 256) String remark) {
    }

    public record RfqSave(@NotNull(message = "请选择客户") Long customerId, Long contactId, Long opportunityId, String currency, String tradeTerm,
                          @NotNull(message = "请选择回复截止日期") LocalDate replyDueDate, @Size(max = 1000) String remark,
                          @Valid List<RfqLineSave> lines, List<Long> fileIds, Integer version) {
    }

    public record AssignReq(Long engineerId, Long costEngineerId) {
    }

    /** 工程评估：可行性 OK / NG + 说明，可关联本厂物料 */
    public record FeasibilityReq(@NotNull Long lineId, @NotBlank(message = "请选择可行性") String feasibility, @Size(max = 512) String remark,
                                 Long materialId) {
    }

    public record CostSheetBrief(Long id, BigDecimal qty, BigDecimal totalCost, BigDecimal suggestedPrice, BigDecimal suggestedPriceCur) {
    }

    public record RfqLineResp(Long id, int lineNo, String customerPartNo, String description, Long materialId, String materialCode, String materialName,
                              String materialSpec, String baseUom, BigDecimal annualQty, String qtyBreaks, BigDecimal targetPrice, LocalDate requiredDate,
                              String feasibility, String feasibilityRemark, String remark, List<CostSheetBrief> costSheets) {
    }

    /**
     * @param canEvaluate 当前用户是被分派的工程师或有分派权限
     * @param canCost     当前用户有成本核算权限
     */
    public record RfqDetail(Long id, String docNo, LocalDate docDate, String rfqStatus, Long customerId, String customerName, String customerStatus,
                            Long contactId, String contactName, Long opportunityId, String currency, String tradeTerm, LocalDate replyDueDate,
                            Long engineerId, String engineerName, Long costEngineerId, String costEngineerName, String closeReason, String remark,
                            Long ownerId, String ownerName, boolean canEvaluate, boolean canCost, boolean costVisible, LocalDateTime createdAt,
                            int version, List<RfqLineResp> lines, List<RelatedDoc> related) {
    }

    // ==================== 成本核算 ====================

    /**
     * @param materialPrices 手工修改的子件单价（本位币/基本单位）：子件 ID → 单价
     * @param adminRate      管理费率（小数）；为空默认 0.05
     * @param profitRate     利润率（小数）；为空默认 0.15
     */
    public record CostSheetReq(@NotNull(message = "请选择核算数量") BigDecimal qty, Long bomId, Map<Long, BigDecimal> materialPrices,
                               BigDecimal toolingTotal, BigDecimal toolingQty, BigDecimal packingFreightCost, BigDecimal adminRate,
                               BigDecimal profitRate) {
    }

    /** @param source STANDARD 标准成本 / PURCHASE 最新采购价 / MANUAL 手工 / NONE 无 */
    public record MaterialCostRow(Long componentId, String code, String name, String spec, String uom, BigDecimal qtyPer, BigDecimal unitPrice,
                                  String source, BigDecimal amount) {
    }

    public record OperationCostRow(Long materialId, String materialCode, int seq, String operation, Long workCenterId, String workCenterName,
                                   BigDecimal runHours, BigDecimal setupHours, BigDecimal laborRate, BigDecimal overheadRate, BigDecimal labor,
                                   BigDecimal overhead, BigDecimal setup) {
    }

    public record CostSheetResp(Long id, Long rfqLineId, BigDecimal qty, Long bomId, String bomNo, BigDecimal materialCost, BigDecimal laborCost,
                                BigDecimal overheadCost, BigDecimal setupCost, BigDecimal toolingTotal, BigDecimal toolingQty, BigDecimal toolingCost,
                                BigDecimal packingFreightCost, BigDecimal adminRate, BigDecimal profitRate, BigDecimal totalCost,
                                BigDecimal suggestedPrice, BigDecimal suggestedPriceCur, String baseCurrency, String currency, BigDecimal targetPrice,
                                BigDecimal targetDiffPct, List<MaterialCostRow> materials, List<OperationCostRow> operations) {
    }

    // ==================== 报价单 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class QuotationQuery extends PageParam {
        private String docNo;
        private Long customerId;
        private Long ownerId;
        /** quoteStatus，逗号分隔；为空默认草稿、审批中、已审核、已发送 */
        private String statuses;
        private Long materialId;
        private LocalDate validFrom;
        private LocalDate validTo;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record QuotationRow(Long id, String docNo, int revision, Long customerId, String customerName, String currency, BigDecimal totalAmount,
                               BigDecimal minMarginRate, boolean lowMargin, LocalDate validUntil, boolean expired, String quoteStatus, Long ownerId,
                               String ownerName, LocalDate docDate) {
    }

    public record QuotationLineSave(@NotNull(message = "请选择物料") Long materialId, @Size(max = 64) String customerPartNo,
                                    @Size(max = 512) String description, String uom, BigDecimal minQty, @NotNull(message = "请填写单价") BigDecimal price,
                                    BigDecimal taxRate, BigDecimal moq, Integer leadTimeDays, BigDecimal toolingFee, Long rfqLineId,
                                    BigDecimal costPrice, @Size(max = 256) String remark) {
    }

    public record QuotationSave(@NotNull(message = "请选择客户") Long customerId, Long contactId, Long opportunityId, String currency,
                                BigDecimal exchangeRate, String tradeTerm, Long paymentTermId, Boolean taxIncluded, LocalDate validUntil,
                                @Size(max = 2000) String terms, @Size(max = 1000) String remark, @Valid List<QuotationLineSave> lines,
                                List<Long> fileIds, Integer version) {
    }

    public record QuotationLineResp(Long id, int lineNo, Long materialId, String materialCode, String materialName, String materialSpec,
                                    String baseUom, String customerPartNo, String description, String uom, BigDecimal minQty, BigDecimal price,
                                    BigDecimal taxRate, BigDecimal costPrice, BigDecimal marginRate, boolean belowFloor, BigDecimal moq,
                                    Integer leadTimeDays, BigDecimal toolingFee, Long rfqLineId, String remark) {
    }

    public record RevisionRow(Long id, String docNo, int revision, String quoteStatus, LocalDate docDate, BigDecimal totalAmount,
                              List<QuotationLineResp> lines) {
    }

    public record QuotationDetail(Long id, String docNo, int revision, LocalDate docDate, String quoteStatus, Long customerId, String customerName,
                                  String customerStatus, String customerLevel, Long contactId, String contactName, Long rfqId, String rfqNo,
                                  Long opportunityId, String currency, BigDecimal exchangeRate, String tradeTerm, Long paymentTermId,
                                  String paymentTermName, boolean taxIncluded, LocalDate validUntil, boolean expired, String terms, String remark,
                                  BigDecimal totalAmount, BigDecimal totalAmountBase, BigDecimal minMarginRate, boolean belowFloor, String lostReason,
                                  String lostRemark, LocalDateTime sentAt, Long parentQuotationId, Long ownerId, String ownerName, boolean costVisible,
                                  LocalDateTime createdAt, int version, List<QuotationLineResp> lines, List<RevisionRow> revisions,
                                  List<RelatedDoc> related) {
    }

    public record LoseReq(@NotBlank(message = "请选择未成交原因") String lostReason, @Size(max = 256) String remark) {
    }

    /** 转订单：选择的行与数量（销售单位） */
    public record ToOrderLine(@NotNull Long quotationLineId, @NotNull BigDecimal qty, LocalDate requiredDate) {
    }

    /** 订单“从报价生成”选单：已审核 / 已发送、未过期的报价单行 */
    public record QuoteOpenLine(Long quotationId, String docNo, int revision, Long customerId, String customerName, String currency, Long lineId,
                                Long materialId, String materialCode, String materialName, String uom, BigDecimal minQty, BigDecimal price,
                                boolean taxIncluded, Integer leadTimeDays, LocalDate validUntil) {
    }
}
