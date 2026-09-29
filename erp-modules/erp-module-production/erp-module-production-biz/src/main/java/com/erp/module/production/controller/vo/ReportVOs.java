package com.erp.module.production.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 报工、不良（需求 09-04、09-06）。数量均为基本单位 */
public final class ReportVOs {

    private ReportVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ReportQuery extends PageParam {
        private String docNo;
        private Long prodOrderId;
        private String prodOrderNo;
        private Long materialId;
        private Integer operationSeq;
        private Long workCenterId;
        private Long workOrderId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private String shift;
        private Long operatorId;
        /** DRAFT / APPROVED，逗号分隔 */
        private String statuses;
        /** NORMAL / REPAIR / SCRAP */
        private String reportKind;
    }

    /** @param stdHours 标准工时 = 合格 × 标准秒 ÷ 3600；efficiency = 标准 ÷ 实际；yieldRate = 合格 ÷ (合格 + 不良 + 报废) */
    public record ReportRow(Long id, String docNo, LocalDate reportDate, String shift, Long prodOrderId, String prodOrderNo, Long materialId,
                            String materialCode, String materialName, int operationSeq, String operation, Long workCenterId, String workCenterName,
                            Long workOrderId, String workOrderNo, String reportKind, BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty,
                            BigDecimal yieldRate, BigDecimal workHours, BigDecimal stdHours, BigDecimal efficiency, String operators, Long toolingId,
                            String toolingCode, String status, LocalDateTime approvedAt) {
    }

    public record OperatorSave(Long userId, @Size(max = 64) String operatorName, BigDecimal hours) {
    }

    public record DefectSave(@NotNull(message = "请选择不良代码") String defectCode, @NotNull(message = "请填写不良数量") BigDecimal qty,
                             @Size(max = 128) String position, @Size(max = 512) String description, List<Long> imageFileIds) {
    }

    public record ReportSave(@NotNull(message = "请选择生产订单") Long prodOrderId, @NotNull(message = "请选择工序") Integer operationSeq, Long workOrderId,
                             Long workCenterId, LocalDate reportDate, String shift, BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty,
                             String scrapReason, BigDecimal workHours, BigDecimal machineHours, Long toolingId, LocalDateTime startTime,
                             LocalDateTime endTime, @Valid List<OperatorSave> operators, @Valid List<DefectSave> defects, @Size(max = 1000) String remark,
                             Integer version) {
    }

    public record OperatorResp(Long userId, String userName, String operatorName, BigDecimal hours) {
    }

    public record DefectResp(Long id, String defectCode, BigDecimal qty, String position, String description, List<Long> imageFileIds,
                             String disposition, BigDecimal repairedQty, BigDecimal scrappedQty, String ncrNo) {
    }

    public record ReportDetail(Long id, String docNo, String status, String reportKind, Long prodOrderId, String prodOrderNo, String prodStatus,
                               Long materialId, String materialCode, String materialName, String batchNo, int operationSeq, String operation,
                               Long workOrderId, String workOrderNo, Long workCenterId, String workCenterName, LocalDate reportDate, String shift,
                               BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty, String scrapReason, BigDecimal workHours,
                               BigDecimal machineHours, Long toolingId, String toolingCode, LocalDateTime startTime, LocalDateTime endTime,
                               Long defectId, String remark, Long ownerId, String ownerName, LocalDateTime approvedAt, String approvedByName,
                               LocalDateTime createdAt, int version, List<OperatorResp> operators, List<DefectResp> defects, List<String> backflushNos) {
    }

    public record OperationOption(int seq, String operation, boolean reportPoint, BigDecimal reportableQty) {
    }

    public record ToolingOption(Long id, String code, String name) {
    }

    /**
     * 报工上下文（扫码或选择生产订单 + 工序）：可报数量 = 投入上限（首道：订单 × (1 + 超产比例)；其他：上道报工点合格）− 已报（含草稿）。
     */
    public record Context(Long prodOrderId, String prodOrderNo, String prodStatus, Long materialId, String materialCode, String materialName,
                          String baseUom, String batchNo, BigDecimal orderQty, Integer operationSeq, String operation, boolean reportPoint,
                          boolean inspectionPoint, Long workCenterId, String workCenterName, Long workOrderId, String workOrderNo, BigDecimal woPlanQty,
                          BigDecimal woReportedQty, BigDecimal inputLimit, BigDecimal reportedQty, BigDecimal reportableQty,
                          List<OperationOption> operations, List<ToolingOption> toolings, boolean requireWorkOrder, boolean autoApprove,
                          LocalDate releasedDate) {
    }

    /** 保存结果：报工单 ID、审核后的状态、提示 */
    public record SaveResult(Long id, String docNo, String status, List<String> warnings) {
    }

    // ==================== 不良 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DefectQuery extends PageParam {
        private Long prodOrderId;
        private String prodOrderNo;
        private Long materialId;
        private Integer operationSeq;
        private String defectCode;
        /** PENDING / REPAIRED / SCRAPPED，逗号分隔；为空默认待处理 */
        private String dispositions;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record DefectRow(Long id, Long reportId, String reportNo, LocalDate reportDate, Long prodOrderId, String prodOrderNo, Long materialId,
                            String materialCode, String materialName, int operationSeq, String operation, String defectCode, BigDecimal qty,
                            String position, String description, List<Long> imageFileIds, String disposition, BigDecimal repairedQty,
                            BigDecimal scrappedQty, BigDecimal pendingQty, String ncrNo, String handledByName, LocalDateTime handledAt) {
    }

    public record RepairReq(@NotNull(message = "请填写返修合格数量") BigDecimal qty) {
    }

    public record ScrapReq(@NotNull(message = "请填写报废数量") BigDecimal qty, String scrapReason) {
    }

    // ==================== 良率 ====================

    @Data
    public static class YieldQuery {
        private LocalDate from;
        private LocalDate to;
        private Long deptId;
        private Long materialId;
        private Integer operationSeq;
        /** PRODUCT / OPERATION / WORK_CENTER / SHIFT / DATE */
        private String groupBy;
    }

    /** firstYield 一次良率 = 一次合格 ÷ 投入；finalYield 最终良率 = (一次合格 + 返修合格) ÷ 投入 */
    public record YieldRow(String key, String label, BigDecimal inputQty, BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty,
                           BigDecimal repairedQty, BigDecimal firstYield, BigDecimal finalYield, BigDecimal defectRate, BigDecimal scrapRate) {
    }

    public record TrendPoint(LocalDate date, BigDecimal firstYield, BigDecimal inputQty) {
    }

    public record ParetoItem(String defectCode, String label, BigDecimal qty, BigDecimal share, BigDecimal cumulative) {
    }

    /** fpy 直通率 = Π 各报工点一次良率（按工序号） */
    public record YieldReport(BigDecimal inputQty, BigDecimal firstYield, BigDecimal fpy, List<YieldRow> rows, List<YieldRow> operations,
                              List<TrendPoint> trend, List<ParetoItem> pareto) {
    }
}
