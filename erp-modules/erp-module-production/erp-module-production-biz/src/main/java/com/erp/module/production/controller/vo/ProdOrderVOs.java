package com.erp.module.production.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.production.controller.vo.CommonVOs.RelatedDoc;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 生产订单（需求 09-01） */
public final class ProdOrderVOs {

    private ProdOrderVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ProdOrderQuery extends PageParam {
        private String docNo;
        private Long materialId;
        private String orderType;
        /** prodStatus，逗号分隔；为空默认已计划、已下达、生产中、暂停 */
        private String statuses;
        private Long deptId;
        private LocalDate planFrom;
        private LocalDate planTo;
        private String salesOrderNo;
        private Long ownerId;
        /** 只看逾期未完工 */
        private Boolean overdue;
        private String columns;
    }

    /** @param progress 完工进度（合格入库 ÷ 计划，0～1） */
    public record ProdOrderRow(Long id, String docNo, String orderType, Long materialId, String materialCode, String materialName, String materialSpec,
                               String baseUom, BigDecimal qty, BigDecimal completedQty, BigDecimal stockedQty, BigDecimal qualifiedStockedQty,
                               BigDecimal scrappedQty, BigDecimal progress, LocalDate planStart, LocalDate planEnd, boolean overdue, Long deptId,
                               String deptName, int priority, String salesOrderNo, String prodStatus, String ownerName, LocalDate docDate) {
    }

    /** 返工订单的投入物料（标准/样品订单按 BOM 生成，不需要传） */
    public record MaterialSave(@NotNull(message = "请选择物料") Long componentId, @NotNull(message = "请填写单位用量") BigDecimal qtyPer,
                               BigDecimal scrapRate, String issueMethod, Integer operationSeq, @Size(max = 256) String remark) {
    }

    public record ProdOrderSave(String orderType, @NotNull(message = "请选择产品") Long materialId, @NotNull(message = "请填写计划数量") BigDecimal qty,
                                Long bomId, Long routingId, @NotNull(message = "请选择计划开工日期") LocalDate planStart,
                                @NotNull(message = "请选择计划完工日期") LocalDate planEnd, Long deptId, Integer priority, Long salesOrderLineId,
                                @Size(max = 64) String batchNo, @Size(max = 1000) String remark, @Valid List<MaterialSave> materials, Integer version) {
    }

    public record MaterialPreview(int lineNo, Long componentId, String code, String name, String spec, String uom, BigDecimal qtyPer, BigDecimal scrapRate,
                                  BigDecimal requiredQty, String issueMethod, Integer operationSeq, BigDecimal availableQty, boolean shortage) {
    }

    public record OperationPreview(int seq, String operation, Long workCenterId, String workCenterName, BigDecimal stdRunSeconds,
                                   BigDecimal stdSetupMinutes, boolean reportPoint, boolean inspectionPoint, boolean outsourced) {
    }

    /** 编辑页预览：BOM / 工艺路线版本、用料、工序、默认车间与提前期 */
    public record Preview(Long bomId, String bomNo, Integer bomVersion, List<Option> boms, Long routingId, String routingNo, List<Option> routings,
                          Long defaultDeptId, int leadTimeDays, List<MaterialPreview> materials, List<OperationPreview> operations) {
    }

    public record Option(Long id, String label, boolean isDefault) {
    }

    /** @param openQty 未领 = max(0, 应领 − 已领 + 已退良品)；netQty 净耗用 = 已领 − 已退 */
    public record MaterialResp(Long id, int lineNo, Long componentId, String code, String name, String spec, String uom, BigDecimal qtyPer,
                               BigDecimal scrapRate, BigDecimal requiredQty, String issueMethod, Integer operationSeq, BigDecimal issuedQty,
                               BigDecimal overIssuedQty, BigDecimal returnedQty, BigDecimal returnedGoodQty, BigDecimal openQty, BigDecimal netQty,
                               BigDecimal availableQty, Long substituteOfId, boolean added, List<SubstituteOption> substitutes, String remark) {
    }

    /** BOM 中定义的替代料：1 个主料 = ratio 个替代料 */
    public record SubstituteOption(Long substituteId, String code, String name, BigDecimal ratio) {
    }

    /**
     * @param stdHours        标准工时 = 合格 × 标准秒 ÷ 3600
     * @param ipqcRejectedId  该工序最近一次 IPQC 判定为拒收时的检验单（警示），否则为空
     */
    public record OperationResp(Long id, int seq, String operation, Long workCenterId, String workCenterName, boolean reportPoint,
                                boolean inspectionPoint, boolean outsourced, BigDecimal stdRunSeconds, BigDecimal stdSetupMinutes, BigDecimal goodQty,
                                BigDecimal defectQty, BigDecimal scrapQty, BigDecimal repairedQty, BigDecimal dispatchedQty, BigDecimal actualHours,
                                BigDecimal stdHours, String opStatus, BigDecimal reportableQty, Long ipqcRejectedId, String ipqcRejectedNo) {
    }

    /** @param finishableQty 可申请完工入库 = 完工 − 已申请；pendingDefectQty 待处理不良 */
    public record ProdOrderDetail(Long id, String docNo, String orderType, String prodStatus, Long materialId, String materialCode, String materialName,
                                  String materialSpec, String baseUom, String tracking, BigDecimal qty, Long bomId, String bomNo, Integer bomVersion,
                                  Long routingId, String routingNo, LocalDate planStart, LocalDate planEnd, LocalDateTime actualStart,
                                  LocalDateTime actualEnd, LocalDateTime releasedAt, int priority, String batchNo, Long salesOrderLineId,
                                  Long salesOrderId, String salesOrderNo, String sourceType, Long sourceId, String sourceNo, BigDecimal completedQty,
                                  BigDecimal scrappedQty, BigDecimal finishedRequestQty, BigDecimal stockedQty, BigDecimal qualifiedStockedQty,
                                  BigDecimal fqcRejectedQty, BigDecimal finishableQty, BigDecimal pendingDefectQty, boolean fqcRequired, Long deptId,
                                  String deptName, Long ownerId, String ownerName, String closeReason, String remark, LocalDateTime createdAt, int version,
                                  List<MaterialResp> materials, List<OperationResp> operations, List<OutputResp> outputs, List<RelatedDoc> related) {
    }

    /** 拆解订单产出：预计产出、已入库 */
    public record OutputResp(Long id, int lineNo, Long componentId, String code, String name, String spec, String uom, BigDecimal qtyPer,
                             BigDecimal expectedQty, BigDecimal receivedQty) {
    }

    /**
     * 调整用料（R05、R06）：id 为空表示新增行；delete 删除（要求已领 = 0）。
     */
    public record AdjustLine(Long id, Long componentId, BigDecimal requiredQty, BigDecimal qtyPer, String issueMethod, Integer operationSeq,
                             Boolean delete, @Size(max = 256) String remark) {
    }

    /** 替代：从原用料行 lineId 中拿出 qty（主料数量）改用替代料，替代数量 = qty × 比例 */
    public record Substitution(@NotNull Long lineId, @NotNull Long substituteId, @NotNull BigDecimal qty) {
    }

    /** @param qty 新计划数量（为空不修改）；修改后用料应领按比例重算（不低于已领 − 已退） */
    public record AdjustReq(@Size(max = 256) String reason, BigDecimal qty, @Valid List<AdjustLine> lines, @Valid List<Substitution> substitutions,
                            Integer version) {
    }

    public record ReleaseReq(Boolean confirmShortage) {
    }

    public record Shortage(Long componentId, String code, String name, String uom, BigDecimal requiredQty, BigDecimal availableQty, BigDecimal shortageQty) {
    }

    /** 齐套检查结果 */
    public record KitCheck(Long prodOrderId, String prodOrderNo, boolean complete, List<Shortage> shortages) {
    }

    public record SuspendReq(String reason) {
    }

    /** @param confirmScrap 确认把未报工的在制、待处理不良按报废处理 */
    public record CloseReq(String reason, Boolean confirmScrap) {
    }

    public record BatchReleaseReq(List<Long> ids, Boolean confirmShortage) {
    }
}
