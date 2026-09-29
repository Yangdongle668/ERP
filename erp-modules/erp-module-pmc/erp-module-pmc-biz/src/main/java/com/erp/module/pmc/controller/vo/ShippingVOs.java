package com.erp.module.pmc.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 出货计划（需求 06-08） */
public final class ShippingVOs {

    private ShippingVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PlanQuery extends PageParam {
        private String docNo;
        private String week;
        /** DRAFT / PUBLISHED / CLOSED，逗号分隔 */
        private String statuses;
    }

    public record PlanRow(Long id, String docNo, String planWeek, String planStatus, int lineCount, BigDecimal planQty, int noticedLineCount,
                          LocalDateTime publishedAt, String ownerName, LocalDateTime createdAt) {
    }

    /** @param shortage 可用不足（计划数量 < 未出货） */
    public record PlanLine(Long id, int lineNo, Long orderLineId, Long orderId, String orderNo, int orderLineNo, Long customerId, String customerName,
                           Long materialId, String materialCode, String materialName, String baseUom, LocalDate dueDate, BigDecimal openQty,
                           BigDecimal availableQty, BigDecimal planQty, LocalDate planShipDate, String transportMode, BigDecimal noticedQty,
                           String lineStatus, String remark, boolean shortage) {
    }

    public record PlanDetail(Long id, String docNo, String planWeek, String planStatus, String remark, LocalDateTime publishedAt, String ownerName,
                             LocalDateTime createdAt, int version, List<PlanLine> lines) {
    }

    public record LineSave(Long id, @NotNull(message = "请选择销售订单行") Long orderLineId, @NotNull(message = "请填写计划数量") BigDecimal planQty,
                           LocalDate planShipDate, String transportMode, @Size(max = 256) String remark) {
    }

    public record PlanSave(@NotBlank(message = "请选择计划周") String planWeek, @Size(max = 1000) String remark, List<LineSave> lines, Integer version) {
    }
}
