package com.erp.module.purchase.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 供应商评估（需求 07-10） */
public final class ScoreVOs {

    private ScoreVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ScoreQuery extends PageParam {
        private String period;
        private Long supplierId;
        private String grade;
        private String statuses;
    }

    public record ScoreRow(Long id, Long supplierId, String supplierCode, String supplierName, String period, int lotCount, int lotPassCount,
                           BigDecimal qualityScore, int dueLineCount, int ontimeLineCount, BigDecimal deliveryScore, BigDecimal priceScore,
                           BigDecimal serviceScore, BigDecimal totalScore, String grade, String status, String comment, LocalDateTime publishedAt,
                           Integer version) {
    }

    public record CalculateReq(@NotBlank(message = "请选择评估期") String period) {
    }

    public record CalculateResult(int count, List<Long> ids) {
    }

    public record ScoreSave(BigDecimal priceScore, BigDecimal serviceScore, @Size(max = 512) String comment, Integer version) {
    }

    /** 明细：本期不合格批次、延误订单行 */
    public record ScoreDetail(Long id, String period, List<Lot> lots, List<DelayedLine> delayedLines) {
    }

    public record Lot(Long receiptId, String receiptNo, String materialCode, String materialName, BigDecimal qty, String inspectStatus,
                      String inspectionNo, LocalDate judgedDate) {
    }

    public record DelayedLine(Long orderId, String orderNo, Integer lineNo, String materialCode, String materialName, BigDecimal qty,
                              LocalDate dueDate, LocalDate firstReceivedDate, BigDecimal receivedQty) {
    }

    public record TrendPoint(String period, BigDecimal totalScore, String grade) {
    }
}
