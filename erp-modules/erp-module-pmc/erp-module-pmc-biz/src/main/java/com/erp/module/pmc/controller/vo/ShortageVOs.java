package com.erp.module.pmc.controller.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 缺料分析（需求 06-06） */
public final class ShortageVOs {

    private ShortageVOs() {
    }

    /** statuses：PLANNED / RELEASED / IN_PROGRESS（为空默认已下达、生产中、已计划）；prodOrderIds 为空按其他条件 */
    public record AnalyzeReq(Long deptId, List<String> statuses, LocalDate planStartFrom, LocalDate planStartTo, List<Long> prodOrderIds) {
    }

    public record AnalyzeResult(String snapshotNo, int orderCount, int shortOrderCount, int shortMaterialCount) {
    }

    public record SupplyItem(String docType, String docNo, LocalDate date, BigDecimal qty) {
    }

    public record OrderRow(Long prodOrderId, String prodOrderNo, int priority, Long productId, String productCode, String productName, BigDecimal qty,
                           LocalDate planStart, String prodStatus, BigDecimal lineKitRate, BigDecimal qtyKitRate, BigDecimal kitableQty, int lineCount,
                           int shortLineCount, LocalDate etaDate, boolean etaLate, boolean hasNoSupply, String salesOrderNo, LocalDate customerDate) {
    }

    public record LineRow(Long prodOrderId, Long componentId, String componentCode, String componentName, String uom, BigDecimal unissuedQty,
                          BigDecimal allocatedQty, BigDecimal shortageQty, List<SupplyItem> supplies, LocalDate etaDate, BigDecimal noSupplyQty,
                          Long buyerId, String buyerName) {
    }

    public record MaterialRow(Long componentId, String componentCode, String componentName, String uom, BigDecimal shortageQty, int orderCount,
                              LocalDate firstNeedDate, List<SupplyItem> supplies, BigDecimal noSupplyQty, Long buyerId, String buyerName,
                              List<String> orderNos) {
    }

    public record SnapshotRow(String snapshotNo, LocalDateTime createdAt, int orderCount, int shortOrderCount, String createdByName) {
    }

    public record PushReq(String snapshotNo, List<Long> componentIds) {
    }
}
