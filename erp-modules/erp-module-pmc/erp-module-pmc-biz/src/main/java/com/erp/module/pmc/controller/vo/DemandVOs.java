package com.erp.module.pmc.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 需求池与交期回复（需求 06-01） */
public final class DemandVOs {

    private DemandVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DemandQuery extends PageParam {
        private Long materialId;
        private Long customerId;
        /** SALES_ORDER / FORECAST / MANUAL，逗号分隔 */
        private String demandTypes;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        /** 只看未满足（默认是） */
        private Boolean openOnly;
        /** 物料计划员 */
        private Long plannerId;
    }

    /** @param availableQty 物料可用库存；wipQty 在制；inTransitQty 在途采购（含委外） */
    public record DemandRow(Long id, String demandType, Long sourceId, String sourceNo, Integer sourceLineNo, Long customerId, String customerName,
                            Long materialId, String materialCode, String materialName, String materialSpec, String baseUom, BigDecimal qty,
                            BigDecimal fulfilledQty, BigDecimal openQty, LocalDate requiredDate, LocalDate customerDate, LocalDate promisedDate,
                            BigDecimal availableQty, BigDecimal wipQty, BigDecimal inTransitQty, int priority, String demandStatus, String remark,
                            String createdByName) {
    }

    public record DemandSave(@NotNull(message = "请选择物料") Long materialId, @NotNull(message = "请填写需求数量") BigDecimal qty,
                             @NotNull(message = "请选择需求日期") LocalDate requiredDate, Integer priority, Long customerId,
                             @Size(max = 256) String remark) {
    }

    // ==================== 交期回复 ====================

    /**
     * 待回复的销售订单行。
     *
     * @param suggestedDate 建议交期（PMC-DMD-R03）；rereply 需重新回复（订单变更了数量或要求交期）
     */
    public record ReplyRow(Long demandId, Long orderId, String orderNo, Integer lineNo, Long orderLineId, Long customerId, String customerName,
                           Long ownerId, String ownerName, Long materialId, String materialCode, String materialName, String materialSpec, String baseUom,
                           BigDecimal qty, BigDecimal openQty, LocalDate customerDate, LocalDate promisedDate, BigDecimal availableQty, BigDecimal wipQty,
                           LocalDate suggestedDate, String suggestBasis, boolean rereply) {
    }

    public record ReplyLine(@NotNull Long demandId, @NotNull(message = "请选择承诺交期") LocalDate promisedDate, @Size(max = 256) String remark) {
    }

    public record ReplyReq(List<ReplyLine> lines) {
    }

    public record KitReq(@NotNull Long materialId, @NotNull BigDecimal qty) {
    }

    /** 单层 BOM 齐套：kitDate 为该子件可齐套日期（库存够为今天，否则按在途覆盖；在途不足按采购提前期） */
    public record KitLine(Long componentId, String code, String name, String uom, BigDecimal requiredQty, BigDecimal availableQty,
                          BigDecimal inTransitQty, LocalDate kitDate, String basis) {
    }

    public record KitResult(Long materialId, BigDecimal qty, LocalDate kitDate, int leadTimeDays, LocalDate suggestedDate, List<KitLine> lines) {
    }
}
