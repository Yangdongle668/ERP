package com.erp.module.quality.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 检验单 */
public final class InspectionVOs {

    private InspectionVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InspectionQuery extends PageParam {
        /** 检验类型，逗号分隔（IQC 菜单含复检：IQC,RECHECK） */
        private String types;
        private String docNo;
        private Long materialId;
        private String batchNo;
        private Long supplierId;
        private Long customerId;
        private String upstreamNo;
        /** 状态，逗号分隔 */
        private String statuses;
        private String result;
        private Long inspectorId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        /** 快捷筛选：PENDING 待检 / OVERDUE 超时 / TODAY 今日已判定 */
        private String quick;
    }

    public record InspectionRow(Long id, String docNo, String inspectType, String ipqcKind, Long materialId, String materialCode, String materialName,
                                String materialSpec, String batchNo, Long supplierId, String supplierName, Long customerId, String customerName,
                                BigDecimal lotQty, int sampleQty, String upstreamType, Long upstreamId, String upstreamNo, String prodOrderNo,
                                Integer operationSeq, long waitMinutes, boolean overdue, String suggestedResult, String result, BigDecimal qualifiedQty,
                                BigDecimal concessionQty, BigDecimal rejectedQty, Long inspectorId, String inspectorName, String status,
                                LocalDateTime createdAt, LocalDateTime judgeAt) {
    }

    /** 快捷筛选计数 */
    public record QuickCounts(long pending, long overdue, long todayJudged) {
    }

    public record ItemResult(Long id, int seq, String itemName, String itemType, String method, String unit, String spec, BigDecimal target,
                             BigDecimal upperLimit, BigDecimal lowerLimit, String defectLevel, boolean isKey, int sampleQty,
                             List<BigDecimal> measuredValues, int ngCount, String itemResult, String remark) {
    }

    public record DefectRow(Long id, String defectCode, String defectName, String defectLevel, int qty, String description, List<Long> imageFileIds) {
    }

    public record InspectionDetail(Long id, String docNo, String inspectType, String ipqcKind, Long materialId, String materialCode, String materialName,
                                   String materialSpec, String baseUom, String batchNo, BigDecimal lotQty, Long supplierId, String supplierName,
                                   Long customerId, String customerName, String sourceType, Long sourceId, String sourceNo, String upstreamType,
                                   Long upstreamId, Long upstreamLineId, String upstreamNo, Long prodOrderId, Integer operationSeq, Long warehouseId,
                                   Long standardId, String standardCode, Integer standardVersion, String standardName, Long standardFileId,
                                   SamplingResult sampling, int sampleQty, Long inspectorId, String inspectorName, LocalDateTime startedAt,
                                   LocalDateTime inspectedAt, String suggestedResult, String result, BigDecimal qualifiedQty, BigDecimal concessionQty,
                                   BigDecimal rejectedQty, int crCount, int maCount, int miCount, Long judgeBy, String judgeName, LocalDateTime judgeAt,
                                   String judgeReason, Long ncrId, String ncrNo, String status, boolean mrbSort, BigDecimal presetConcessionQty,
                                   BigDecimal presetRejectedQty, List<Long> transferIds, int rejudgeCount, boolean rejudgePending, String remark,
                                   LocalDateTime createdAt, int version, List<ItemResult> items, List<DefectRow> defects) {
    }

    public record ItemSave(@NotNull Long id, List<BigDecimal> measuredValues, Integer ngCount, @Size(max = 256) String remark) {
    }

    public record DefectSave(@NotBlank(message = "请选择缺陷代码") String defectCode, String defectLevel, @NotNull(message = "请填写缺陷数量") Integer qty,
                             @Size(max = 512) String description, List<Long> imageFileIds) {
    }

    public record ResultsSave(List<ItemSave> items, List<DefectSave> defects, @Size(max = 512) String remark, Integer version) {
    }

    /**
     * @param result QUALIFIED / REJECTED / SORTED（特采只能走 MRB）
     */
    public record JudgeReq(@NotBlank(message = "请选择判定结果") String result, BigDecimal qualifiedQty, BigDecimal rejectedQty,
                           @Size(max = 512) String reason) {
    }

    /** 手工新建：IPQC（首件 / 巡检 / 末件，选择生产订单 + 工序）或复检 */
    public record ManualCreate(@NotBlank(message = "请选择检验类型") String inspectType, String ipqcKind, Long prodOrderId, Integer operationSeq,
                               Long materialId, String batchNo, @NotNull(message = "请填写送检数量") BigDecimal lotQty, Long warehouseId,
                               @Size(max = 512) String remark) {
    }

    public record BatchJudgeResult(int success, List<String> errors) {
    }

    /** 新建 IPQC 时可选的生产订单 */
    public record ProdOrderOption(Long id, String docNo, Long materialId, String materialCode, String materialName, BigDecimal qty, String prodStatus,
                                  List<OperationOption> operations) {
    }

    public record OperationOption(int seq, String operation) {
    }
}
