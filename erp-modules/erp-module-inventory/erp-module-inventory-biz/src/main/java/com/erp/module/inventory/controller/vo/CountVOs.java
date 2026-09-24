package com.erp.module.inventory.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 盘点接口的请求/响应（需求 08-06） */
public final class CountVOs {

    private CountVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CountQuery extends PageParam {
        private String docNo;
        private String countType;
        private Long warehouseId;
        /** 盘点状态，逗号分隔；为空时默认未审核、未作废 */
        private String countStatuses;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record CountRow(Long id, String docNo, String countType, String warehouseNames, String scopeSummary, int lineCount, int inputCount,
                           int diffCount, BigDecimal diffAmount, String countStatus, String createdByName, LocalDate docDate) {
    }

    public record CountSave(@NotNull(message = "请选择盘点类型") String countType, @NotEmpty(message = "请选择仓库") List<Long> warehouseIds,
                            List<Long> categoryIds, List<Long> locationIds, List<Long> materialIds, Boolean includeZero, Boolean blindCount,
                            LocalDate docDate, @Size(max = 512) String remark, Integer version) {
    }

    public record CountDetail(Long id, String docNo, String countType, List<Long> warehouseIds, String warehouseNames, List<Long> categoryIds,
                              List<Long> locationIds, List<Long> materialIds, String scopeSummary, boolean includeZero, boolean blindCount,
                              LocalDateTime snapshotAt, String countStatus, String status, LocalDate docDate, String remark, Long gainInId,
                              Long lossOutId, List<RelatedDoc> adjustDocs, String createdByName, LocalDateTime createdAt, int version,
                              int lineCount, int inputCount, int diffCount, int recountCount, BigDecimal diffAmount, boolean bookVisible) {
    }

    /** 盘盈入库单、盘亏出库单 */
    public record RelatedDoc(String docType, Long id, String docNo, String status) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LineQuery extends PageParam {
        /** ALL / UNINPUT / DIFF / RECOUNT */
        private String filter;
        private String keyword;
    }

    /** bookQty、diffQty 在盲盘且无审核权限时为空（前端显示 ***） */
    public record CountLineRow(Long id, int lineNo, Long warehouseId, String warehouseName, Long locationId, String locationCode, Long materialId,
                               String materialCode, String materialName, String materialSpec, String baseUom, String batchNo, BigDecimal bookQty,
                               BigDecimal countQty, BigDecimal recountQty, BigDecimal finalQty, BigDecimal diffQty, BigDecimal diffAmount,
                               boolean needRecount, boolean added, String reason, String counterName, LocalDateTime countedAt, String remark) {
    }

    public record LineInput(@NotNull Long id, @DecimalMin(value = "0", message = "实盘数量不能小于 0") BigDecimal countQty,
                            @DecimalMin(value = "0", message = "复盘数量不能小于 0") BigDecimal recountQty, @Size(max = 32) String reason,
                            @Size(max = 256) String remark) {
    }

    public record AddLine(@NotNull(message = "请选择仓库") Long warehouseId, Long locationId, @NotNull(message = "请选择物料") Long materialId,
                          @Size(max = 64) String batchNo,
                          @NotNull(message = "请填写实盘数量") @DecimalMin(value = "0", inclusive = false, message = "实盘数量必须大于 0") BigDecimal countQty,
                          @Size(max = 32) String reason, @Size(max = 256) String remark) {
    }

    /** 生成盘点表前的提示：范围内尚未确认的单据（R01，不阻止） */
    public record PendingDocs(List<String> docNos) {
    }
}
