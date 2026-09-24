package com.erp.module.inventory.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.inventory.api.doc.JudgeResult;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.doc.StockOutType;
import com.erp.module.inventory.api.doc.TransferType;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 入库单、出库单、调拨单接口的请求/响应（需求 08-03/04/05） */
public final class StockDocVOs {

    private StockDocVOs() {
    }

    // ==================== 通用 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DocQuery extends PageParam {
        private String docNo;
        /** 入库/出库/调拨类型，逗号分隔 */
        private String types;
        private Long warehouseId;
        /** 调拨：调入仓 */
        private Long toWarehouseId;
        /** 状态，逗号分隔 */
        private String statuses;
        private String sourceNo;
        private Long materialId;
        private Long supplierId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        /** 快捷筛选：TODO 待处理 / TODAY 今日已确认 */
        private String quick;
        /** 导出：勾选的单据 ID */
        private String ids;
    }

    /** 列表行（入库、出库、调拨共用） */
    public record DocRow(Long id, String docNo, String docType, String type, Long warehouseId, String warehouseName, WarehouseType warehouseType,
                         Long toWarehouseId, String toWarehouseName, WarehouseType toWarehouseType, String sourceType, Long sourceId, String sourceNo,
                         String partnerName, String materialSummary, int lineCount, BigDecimal totalQty, BigDecimal amount, LocalDate docDate,
                         String status, boolean manual, String confirmedByName, LocalDateTime confirmedAt, String createdByName) {
    }

    public record ReasonReq(@Size(max = 256) String reason) {
    }

    public record BatchReq(List<Long> ids) {
    }

    public record BatchResult(int success, List<Failure> failures) {
    }

    public record Failure(Long id, String docNo, String message) {
    }

    /** 快捷筛选计数 */
    public record QuickCounts(long todo, long today) {
    }

    // ==================== 入库单 ====================

    public record StockInDetail(Long id, String docNo, StockInType inType, Long warehouseId, String warehouseName, WarehouseType warehouseType,
                                boolean locationEnabled, LocalDate docDate, String status, boolean manual, String reason, String sourceType,
                                Long sourceId, String sourceNo, LocalDate sourceDate, Long supplierId, Long customerId, String remark,
                                Long confirmedBy, String confirmedByName, LocalDateTime confirmedAt, String rejectReason, String createdByName,
                                LocalDateTime createdAt, int version, List<StockInLineResp> lines) {
    }

    public record StockInLineResp(Long id, int lineNo, Long materialId, String materialCode, String materialName, String materialSpec,
                                  String baseUom, String tracking, Integer shelfLifeDays, String uom, BigDecimal qty, BigDecimal baseQty,
                                  Long locationId, String locationCode, String batchNo, String supplierBatchNo, LocalDate productionDate,
                                  LocalDate expireDate, List<String> serialNos, BigDecimal unitCost, BigDecimal amount, Long sourceLineId,
                                  String remark) {
    }

    /** 新建/修改入库单：手工单可改全部；业务生成的单据只能补充库位、批次属性、序列号、备注（按行 ID 匹配） */
    public record StockInSave(@NotNull(message = "请选择仓库") Long warehouseId, LocalDate docDate, @Size(max = 32) String reason,
                              @Size(max = 512) String remark, @Valid List<StockInLineSave> lines, List<Long> fileIds, Integer version) {
    }

    public record StockInLineSave(Long id, Long materialId, @Size(max = 16) String uom,
                                  @DecimalMin(value = "0", inclusive = false, message = "数量必须大于 0") BigDecimal qty,
                                  Long locationId, @Size(max = 64) String batchNo, @Size(max = 64) String supplierBatchNo,
                                  LocalDate productionDate, LocalDate expireDate, List<String> serialNos,
                                  @DecimalMin(value = "0", message = "单价不能小于 0") BigDecimal unitCost, @Size(max = 256) String remark) {
    }

    /** 确认：可同时带补充信息（为空时直接确认） */
    public record ConfirmReq(LocalDate docDate, @Valid List<StockInLineSave> lines, @Valid List<StockOutLineSave> outLines,
                             @Valid List<TransferLineSave> transferLines) {
    }

    // ==================== 出库单 ====================

    public record StockOutDetail(Long id, String docNo, StockOutType outType, Long warehouseId, String warehouseName, WarehouseType warehouseType,
                                 boolean locationEnabled, LocalDate docDate, String status, boolean manual, String reason, Long receiverDeptId,
                                 Long receiverId, String receiverName, String sourceType, Long sourceId, String sourceNo, LocalDate sourceDate,
                                 Long supplierId, Long customerId, String remark, Long confirmedBy, String confirmedByName, LocalDateTime confirmedAt,
                                 String rejectReason, String createdByName, LocalDateTime createdAt, int version, List<StockOutLineResp> lines) {
    }

    /** availableQty 为本仓库该物料当前可用量（基本单位） */
    public record StockOutLineResp(Long id, int lineNo, Long materialId, String materialCode, String materialName, String materialSpec,
                                   String baseUom, String tracking, String issueRule, String uom, BigDecimal requestQty, BigDecimal qty,
                                   BigDecimal baseQty, BigDecimal availableQty, Long locationId, String locationCode, String batchNo,
                                   List<String> serialNos, Long sourceLineId, String remark) {
    }

    public record StockOutSave(@NotNull(message = "请选择仓库") Long warehouseId, LocalDate docDate, Long receiverDeptId, Long receiverId,
                               @Size(max = 32) String reason, @Size(max = 512) String remark, @Valid List<StockOutLineSave> lines,
                               List<Long> fileIds, Integer version) {
    }

    /** requestQty 为空时 = qty（手工单）；业务单按 sourceLineId 拆分批次行 */
    public record StockOutLineSave(Long id, Long materialId, @Size(max = 16) String uom, BigDecimal requestQty,
                                   @DecimalMin(value = "0", message = "数量不能小于 0") BigDecimal qty, Long locationId,
                                   @Size(max = 64) String batchNo, List<String> serialNos, Long sourceLineId, @Size(max = 256) String remark) {
    }

    /** 自动分配结果：shortage 为该行库存不足的数量 */
    public record AllocatedLine(Long id, Long materialId, String materialCode, String uom, BigDecimal requestQty, BigDecimal qty, Long locationId,
                                String locationCode, String batchNo, Long sourceLineId, BigDecimal shortage) {
    }

    // ==================== 调拨单 ====================

    public record TransferDetail(Long id, String docNo, TransferType transferType, Long fromWarehouseId, String fromWarehouseName,
                                 WarehouseType fromWarehouseType, boolean fromLocationEnabled, Long toWarehouseId, String toWarehouseName,
                                 WarehouseType toWarehouseType, boolean toLocationEnabled, LocalDate docDate, String status, String reason,
                                 Long inspectionId, String sourceType, Long sourceId, String sourceNo, String remark, Long confirmedBy,
                                 String confirmedByName, LocalDateTime confirmedAt, String createdByName, LocalDateTime createdAt, int version,
                                 List<TransferLineResp> lines) {
    }

    public record TransferLineResp(Long id, int lineNo, Long materialId, String materialCode, String materialName, String materialSpec,
                                   String baseUom, String tracking, BigDecimal qty, BigDecimal availableQty, String batchNo, Long fromLocationId,
                                   String fromLocationCode, Long toLocationId, String toLocationCode, List<String> serialNos, JudgeResult judgeResult,
                                   String remark) {
    }

    public record TransferSave(TransferType transferType, @NotNull(message = "请选择调出仓") Long fromWarehouseId,
                               @NotNull(message = "请选择调入仓") Long toWarehouseId, LocalDate docDate, @Size(max = 256) String reason,
                               @Size(max = 512) String remark, @Valid List<TransferLineSave> lines, Integer version) {
    }

    public record TransferLineSave(Long id, Long materialId, @DecimalMin(value = "0", inclusive = false, message = "数量必须大于 0") BigDecimal qty,
                                   @Size(max = 64) String batchNo, Long fromLocationId, Long toLocationId, List<String> serialNos,
                                   @Size(max = 256) String remark) {
    }
}
