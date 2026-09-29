package com.erp.module.pmc.service.mrp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** MRP 运算的内存模型（快照输入与计算结果） */
public final class MrpModel {

    private MrpModel() {
    }

    /** 子件用量：每 1 个父件需要的数量（已除以基数、含损耗） */
    public record Comp(Long componentId, BigDecimal qtyPer) {
    }

    /** 物料计划信息；sourceType：PURCHASE / MAKE / OUTSOURCE */
    public record Mat(Long id, String code, String uom, int scale, String sourceType, boolean enabled, int leadTimeDays, BigDecimal safetyStock, String orderPolicy,
                      BigDecimal fixedLotQty, Integer periodDays, BigDecimal moq, BigDecimal mpq, Long plannerId, Long buyerId, Long bomId,
                      List<Comp> comps) {
        public boolean make() {
            return "MAKE".equals(sourceType) || "OUTSOURCE".equals(sourceType);
        }
    }

    /**
     * 需求。type：SALES_ORDER / FORECAST / MANUAL / MPS / SAFETY_STOCK / PARENT（上层计划订单）/ ALLOCATION（已有生产订单的未领）。
     */
    public static final class Demand {
        final Long materialId;
        final LocalDate date;
        final BigDecimal qty;
        final String type;
        final Long sourceId;
        final String sourceNo;
        final Long parentMaterialId;
        final Planned parent;

        public Demand(Long materialId, LocalDate date, BigDecimal qty, String type, Long sourceId, String sourceNo, Long parentMaterialId, Planned parent) {
            this.materialId = materialId;
            this.date = date;
            this.qty = qty;
            this.type = type;
            this.sourceId = sourceId;
            this.sourceNo = sourceNo;
            this.parentMaterialId = parentMaterialId;
            this.parent = parent;
        }
    }

    /** 已有供应。type：OPENING（期初可用）/ PURCHASE（采购、委外在途）/ QC（待检）/ WIP（生产订单） */
    public static final class Supply {
        final Long materialId;
        final String type;
        final String docType;
        final Long docId;
        final String docNo;
        final Long lineId;
        final LocalDate date;
        final BigDecimal qty;
        BigDecimal remaining;
        LocalDate firstUse;

        public Supply(Long materialId, String type, String docType, Long docId, String docNo, Long lineId, LocalDate date, BigDecimal qty) {
            this.materialId = materialId;
            this.type = type;
            this.docType = docType;
            this.docId = docId;
            this.docNo = docNo;
            this.lineId = lineId;
            this.date = date;
            this.qty = qty;
            this.remaining = qty;
        }
    }

    /** 计划订单（建议） */
    public static final class Planned {
        int seq;
        Long materialId;
        String type;
        BigDecimal qty;
        BigDecimal net;
        LocalDate requiredDate;
        LocalDate releaseDate;
        boolean late;
        BigDecimal remaining;
        Long resultId;
        final List<Peg> pegs = new ArrayList<>();

        public int seq() {
            return seq;
        }
    }

    /** 需求追溯：计划订单覆盖的需求 */
    public record Peg(String demandType, Long sourceId, String sourceNo, Long parentMaterialId, Planned parent, BigDecimal qty, LocalDate date) {
    }

    /** 例外信息 */
    public record Exception(Long materialId, String type, String docType, Long docId, String docNo, Long lineId, LocalDate supplyDate,
                            LocalDate suggestedDate, BigDecimal qty, String message) {
    }

    /** 供需平衡明细 */
    public record Balance(Long materialId, LocalDate date, String type, String docNo, Long parentMaterialId, BigDecimal demand, BigDecimal supply) {
    }

    /** 输入快照 */
    public record Input(LocalDate today, LocalDate horizonEnd, int toleranceDays, boolean includeSafety, Map<Long, Mat> mats, List<Demand> demands,
                        List<Supply> supplies) {
    }

    /** 输出 */
    public record Output(List<Planned> planned, List<Exception> exceptions, List<Balance> balances, int materialCount) {
    }
}
