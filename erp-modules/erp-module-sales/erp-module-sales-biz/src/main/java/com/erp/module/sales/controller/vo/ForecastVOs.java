package com.erp.module.sales.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 销售预测（需求 04-05），矩阵录入：行 = 客户（可空）+ 物料，列 = 月份 */
public final class ForecastVOs {

    private ForecastVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ForecastQuery extends PageParam {
        /** 单号或标题 */
        private String keyword;
        /** 逗号分隔：DRAFT / APPROVED（已发布）/ CLOSED；为空默认草稿和已发布 */
        private String statuses;
        /** 包含该月份（yyyyMM） */
        private String period;
        private Long ownerId;
        private Long materialId;
    }

    public record ForecastRow(Long id, String docNo, String title, String startPeriod, String endPeriod, int lineCount, BigDecimal totalQty,
                              BigDecimal consumedQty, BigDecimal consumeRate, String status, Long ownerId, String ownerName, LocalDateTime publishedAt,
                              LocalDateTime createdAt) {
    }

    public record CellSave(@NotBlank String period, BigDecimal qty) {
    }

    public record RowSave(Long customerId, @NotNull(message = "请选择物料") Long materialId, @Size(max = 256) String remark,
                          @Valid List<CellSave> cells) {
    }

    public record ForecastSave(@NotBlank(message = "请填写标题") @Size(max = 128) String title,
                               @NotBlank(message = "请选择开始月份") String startPeriod, @NotBlank(message = "请选择结束月份") String endPeriod,
                               @Size(max = 1000) String remark, @Valid List<RowSave> rows, Integer version) {
    }

    public record CellResp(Long lineId, String period, BigDecimal qty, BigDecimal consumedQty) {
    }

    public record RowResp(Long customerId, String customerName, Long materialId, String materialCode, String materialName, String materialSpec,
                          String baseUom, String remark, List<CellResp> cells, BigDecimal totalQty, BigDecimal totalConsumed) {
    }

    public record ForecastDetail(Long id, String docNo, String title, String startPeriod, String endPeriod, List<String> periods, String status,
                                 LocalDateTime publishedAt, String closeReason, Long revisedFromId, String revisedFromNo, String remark,
                                 Long ownerId, String ownerName, LocalDateTime createdAt, int version, List<RowResp> rows) {
    }

    public record ConsumptionRow(Long id, Long orderId, String orderNo, Long orderLineId, Integer lineNo, String customerName, BigDecimal qty,
                                 LocalDateTime createdAt) {
    }
}
