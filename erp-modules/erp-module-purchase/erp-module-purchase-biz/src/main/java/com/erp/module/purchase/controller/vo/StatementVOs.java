package com.erp.module.purchase.controller.vo;

import com.erp.common.result.PageParam;
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

/** 供应商对账（需求 07-09） */
public final class StatementVOs {

    private StatementVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class StatementQuery extends PageParam {
        private String docNo;
        private Long supplierId;
        private String statuses;
        private Long ownerId;
        private LocalDate periodFrom;
        private LocalDate periodTo;
    }

    public record StatementRow(Long id, String docNo, Long supplierId, String supplierName, LocalDate periodFrom, LocalDate periodTo, String currency,
                               BigDecimal goodsAmount, BigDecimal returnAmount, BigDecimal adjustAmount, BigDecimal totalAmount, String status,
                               LocalDateTime supplierConfirmedAt, String ownerName) {
    }

    /**
     * 对账明细：货款、退货、委外加工费按来源行（sourceType + sourceLineId）与数量保存，单价与金额由系统按来源重新计算；
     * 调整行填写 totalAmount（含税，扣款为负）、taxRate、remark。
     */
    public record LineSave(@NotBlank String lineType, String sourceType, Long sourceLineId, BigDecimal qty, BigDecimal totalAmount,
                           BigDecimal taxRate, LocalDate bizDate, @Size(max = 256) String remark) {
    }

    public record StatementSave(@NotNull(message = "请选择供应商") Long supplierId, String currency, @NotNull(message = "请选择对账区间") LocalDate periodFrom,
                                @NotNull(message = "请选择对账区间") LocalDate periodTo, @Size(max = 1000) String remark, @Valid List<LineSave> lines,
                                List<Long> fileIds, Integer version) {
    }

    /** @param remainingQty 可对账数量（加载候选时） */
    public record LineResp(Long id, Integer lineNo, String lineType, String sourceType, Long sourceId, Long sourceLineId, String sourceNo,
                           String orderNo, Long materialId, String materialCode, String materialName, String baseUom, LocalDate bizDate, BigDecimal qty,
                           BigDecimal remainingQty, BigDecimal priceInclTax, BigDecimal taxRate, BigDecimal amount, BigDecimal taxAmount,
                           BigDecimal totalAmount, String remark) {
    }

    public record StatementDetail(Long id, String docNo, LocalDate docDate, String status, Long supplierId, String supplierName, LocalDate periodFrom,
                                  LocalDate periodTo, String currency, BigDecimal exchangeRate, BigDecimal goodsAmount, BigDecimal returnAmount,
                                  BigDecimal adjustAmount, BigDecimal totalAmount, BigDecimal taxAmount, LocalDateTime supplierConfirmedAt,
                                  String supplierConfirmer, String remark, Long ownerId, String ownerName, LocalDateTime createdAt, Integer version,
                                  boolean priceVisible, List<LineResp> lines) {
    }

    public record ConfirmReq(@NotBlank(message = "请填写供应商确认人") @Size(max = 64) String confirmer, LocalDateTime confirmedAt,
                             List<Long> fileIds) {
    }

    public record BatchGenerateReq(@NotNull LocalDate periodFrom, @NotNull LocalDate periodTo) {
    }
}
