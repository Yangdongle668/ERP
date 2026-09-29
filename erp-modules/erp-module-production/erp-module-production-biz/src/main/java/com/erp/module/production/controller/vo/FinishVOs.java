package com.erp.module.production.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 完工入库（需求 09-05）。数量为基本单位 */
public final class FinishVOs {

    private FinishVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class FinishQuery extends PageParam {
        private Long prodOrderId;
        private String prodOrderNo;
        private Long materialId;
        /** SUBMITTED / STOCKED / JUDGED / CANCELED，逗号分隔 */
        private String statuses;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record FinishRow(Long id, String docNo, Long prodOrderId, String prodOrderNo, Long materialId, String materialCode, String materialName,
                            BigDecimal qty, String batchNo, boolean fqcRequired, Long warehouseId, String warehouseName, String stockInNos,
                            BigDecimal stockedQty, BigDecimal qualifiedQty, BigDecimal rejectedQty, String finishStatus, String ownerName, LocalDate docDate,
                            String remark) {
    }

    public record FinishReq(@NotNull(message = "请填写入库数量") BigDecimal qty, @Size(max = 64) String batchNo, List<String> serialNos,
                            @Size(max = 1000) String remark) {
    }
}
