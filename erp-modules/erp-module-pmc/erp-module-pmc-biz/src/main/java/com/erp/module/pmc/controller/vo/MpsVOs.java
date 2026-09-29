package com.erp.module.pmc.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** MPS 主生产计划（需求 06-02） */
public final class MpsVOs {

    private MpsVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MpsQuery extends PageParam {
        private String docNo;
        /** DRAFT / PUBLISHED / CLOSED，逗号分隔 */
        private String statuses;
        /** 覆盖该周的 MPS */
        private String week;
    }

    public record MpsRow(Long id, String docNo, String title, String startWeek, String endWeek, String mpsStatus, int materialCount,
                         LocalDateTime publishedAt, String ownerName, LocalDateTime createdAt) {
    }

    public record MpsSave(@NotBlank(message = "请填写标题") @Size(max = 128) String title, @NotBlank(message = "请选择开始周") String startWeek,
                          @NotBlank(message = "请选择结束周") String endWeek, @Size(max = 1000) String remark, Integer version) {
    }

    public record MpsDetail(Long id, String docNo, String title, String startWeek, String endWeek, String mpsStatus, String remark,
                            LocalDateTime publishedAt, Long copiedFromId, String copiedFromNo, String ownerName, LocalDateTime createdAt, int version) {
    }

    /** 矩阵单元：demand 需求（需求池该周未满足）、wip 在制完工（计划完工在该周）、planned 计划生产、projected 预计结存；locked 过去的周 */
    public record Cell(String week, BigDecimal demandQty, BigDecimal wipQty, BigDecimal plannedQty, BigDecimal projectedQty, boolean locked,
                       String remark) {
    }

    public record MatrixRow(Long materialId, String materialCode, String materialName, String materialSpec, String baseUom, BigDecimal safetyStock,
                            BigDecimal openingQty, BigDecimal mpq, List<Cell> cells) {
    }

    public record Matrix(Long mpsId, String mpsStatus, List<String> weeks, List<MatrixRow> rows) {
    }

    public record CellSave(String week, BigDecimal plannedQty, @Size(max = 128) String remark) {
    }

    public record RowSave(Long materialId, List<CellSave> cells) {
    }

    /** 整体保存：rows 中没有的物料删除 */
    public record MatrixSave(List<RowSave> rows, Integer version) {
    }

    public record GenerateReq(List<Long> materialIds) {
    }

    public record CapCell(String week, BigDecimal loadHours, BigDecimal capacityHours, boolean overloaded) {
    }

    public record CapRow(Long workCenterId, String workCenterCode, String workCenterName, List<CapCell> cells) {
    }
}
