package com.erp.module.shipping.controller.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 装箱（11-02 第 3.2 节） */
public final class PackingVOs {

    private PackingVOs() {
    }

    /** 待装：通知行 + 批次的实拣数量与已装数量 */
    public record PackItem(Long noticeLineId, int lineNo, Long materialId, String materialCode, String materialName, String customerPartNo, String batchNo,
                           String uom, BigDecimal pickedQty, BigDecimal packedQty, BigDecimal remainingQty, BigDecimal unitNetWeight,
                           BigDecimal unitGrossWeight) {
    }

    public record CartonLineVO(Long id, Long noticeLineId, Integer lineNo, Long materialId, String materialCode, String materialName, String customerPartNo,
                               String batchNo, BigDecimal qty, String serialNos) {
    }

    public record CartonVO(Long id, int cartonNo, String cartonSpec, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, BigDecimal grossWeightKg,
                           BigDecimal netWeightKg, BigDecimal cbm, String palletNo, Long shipmentId, String shipmentNo, List<CartonLineVO> lines) {
    }

    public record PackingTotals(int cartonCount, BigDecimal qty, BigDecimal grossWeight, BigDecimal netWeight, BigDecimal cbm) {
    }

    public record PackingView(Long noticeId, String noticeNo, String noticeStatus, Long customerId, String customerName, LocalDate shipDate,
                              boolean oqcRequired, String oqcResult, boolean packingEnabled, boolean editable, boolean complete, List<PackItem> items,
                              List<CartonVO> cartons, PackingTotals totals) {
    }

    /** 按规格批量装箱：每箱数量，尾箱为余数 */
    public record BatchPackReq(@NotNull Long noticeLineId, String batchNo, @NotNull BigDecimal qtyPerCarton, BigDecimal totalQty, String cartonSpec,
                               BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, BigDecimal tareWeightKg, String palletNo) {
    }

    public record CartonLineSave(@NotNull Long noticeLineId, String batchNo, @NotNull BigDecimal qty, String serialNos) {
    }

    /** 手工新增 / 编辑箱（可混装） */
    public record CartonSave(String cartonSpec, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, BigDecimal grossWeightKg, BigDecimal netWeightKg,
                             BigDecimal tareWeightKg, String palletNo, @NotEmpty @Valid List<CartonLineSave> lines) {
    }

    public record PackCompleteReq(boolean requestOqc) {
    }
}
