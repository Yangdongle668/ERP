package com.erp.module.quality.controller.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 质量追溯与报表 */
public final class ReportVOs {

    private ReportVOs() {
    }

    // ==================== 追溯 ====================

    /** 一个批次上的检验记录 */
    public record BatchInspection(Long inspectionId, String docNo, String inspectType, String result, BigDecimal qualifiedQty, BigDecimal concessionQty,
                                  BigDecimal rejectedQty, Long ncrId, String ncrNo, java.time.LocalDateTime judgeAt) {
    }

    /** 追溯节点：生产订单 + 产品批次 + 投入批次，并叠加两端批次的质量信息 */
    public record TraceRow(int level, Long prodOrderId, String prodOrderNo, Long productMaterialId, String productCode, String productName,
                           String productBatchNo, Long componentMaterialId, String componentCode, String componentName, String componentBatchNo,
                           BigDecimal qty, boolean productConcession, boolean productFrozen, boolean componentConcession, boolean componentFrozen,
                           List<BatchInspection> productInspections, List<BatchInspection> componentInspections) {
    }

    /** 批次库存分布与出货 */
    public record BatchStock(Long materialId, String materialCode, String materialName, String batchNo, boolean frozen, List<WarehouseQty> warehouses,
                             BigDecimal onHandQty, BigDecimal shippedQty, List<ShipRow> shipments) {
    }

    public record WarehouseQty(Long warehouseId, String warehouseName, BigDecimal qty) {
    }

    public record ShipRow(LocalDate bizDate, String docNo, String sourceNo, BigDecimal qty) {
    }

    public record TraceResult(Long materialId, String materialCode, String materialName, String batchNo, boolean frozen, List<BatchInspection> inspections,
                              List<TraceRow> nodes, List<BatchStock> affected) {
    }

    public record FreezeItem(Long materialId, String batchNo) {
    }

    public record FreezeReq(Long ncrId, List<FreezeItem> items) {
    }

    public record FreezeResult(int frozen, List<String> skipped) {
    }

    // ==================== 报表 ====================

    public record ReportQuery(LocalDate from, LocalDate to, Long supplierId, Long categoryId, Long materialId, String source) {
    }

    /** 批次合格率行（维度：供应商 / 物料 / 月份 / 类型） */
    public record LotRow(String key, String name, int lots, int qualifiedLots, int concessionLots, int rejectedLots, int sortedLots, BigDecimal passRate,
                         BigDecimal inspectedQty, int sampleQty, int defectQty, BigDecimal defectRate) {
    }

    public record IqcReport(LotRow total, List<LotRow> bySupplier, List<LotRow> byMaterial, List<LotRow> byMonth) {
    }

    public record ParetoRow(String code, String name, int qty, BigDecimal pct, BigDecimal cumulativePct) {
    }

    public record ProcessReport(LotRow total, List<LotRow> byMonth, List<LotRow> byMaterial, List<ParetoRow> pareto) {
    }

    public record OutgoingReport(LotRow fqc, BigDecimal fqcFirstPassRate, LotRow oqc, int shipmentLots, int complaints, BigDecimal complaintRate,
                                 List<LotRow> fqcByMonth, List<LotRow> oqcByMonth) {
    }

    public record CountRow(String key, String name, int count, BigDecimal qty) {
    }

    public record CapaOverdue(Long id, String docNo, String title, String leaderName, LocalDate dueDate, int overdueDays, int currentStep) {
    }

    public record NcrCapaComplaintReport(int ncrCount, BigDecimal ncrAvgCloseDays, List<CountRow> ncrBySource, List<CountRow> ncrByResponsibility,
                                         List<CountRow> ncrByDisposition, List<CountRow> ncrByMaterial, int capaCount, int capaClosed,
                                         BigDecimal capaOnTimeRate, BigDecimal capaAvgDays, List<CapaOverdue> capaOverdue, int complaintCount,
                                         BigDecimal replyOnTimeRate, List<CountRow> complaintByCustomer, List<CountRow> complaintByType,
                                         List<CountRow> complaintBySeverity, List<CountRow> complaintByMonth) {
    }
}
