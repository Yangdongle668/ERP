package com.erp.module.shipping.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 拣货（11-02） */
public final class PickingVOs {

    private PickingVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PickingQuery extends PageParam {
        private String docNo;
        private String noticeNo;
        private Long customerId;
        private Long warehouseId;
        /** 逗号分隔；OPEN = 待拣、拣货中 */
        private String statuses;
        private LocalDate shipDateFrom;
        private LocalDate shipDateTo;
    }

    public record PickingRow(Long id, String docNo, Long noticeId, String noticeNo, Long customerId, String customerName, LocalDate shipDate,
                             Long warehouseId, String warehouseName, int lineCount, BigDecimal suggestedQty, BigDecimal pickedQty, String pickingStatus,
                             Long pickerId, String pickerName, LocalDateTime startedAt, LocalDateTime completedAt, LocalDateTime createdAt) {
    }

    public record PickingLineVO(Long id, int lineNo, Long noticeLineId, Integer noticeLineNo, Long materialId, String materialCode, String materialName,
                                String spec, String baseUom, Long locationId, String batchNo, BigDecimal suggestedQty, BigDecimal pickedQty, String serialNos,
                                boolean shortage) {
    }

    /** 每个通知行的拣货汇总（完成拣货时校验 R03） */
    public record NoticeLineSum(Long noticeLineId, int lineNo, Long materialId, String materialCode, String materialName, String baseUom, BigDecimal qty,
                                BigDecimal pickedQty, BigDecimal shortageQty) {
    }

    public record PickingDetail(Long id, String docNo, String status, String pickingStatus, Long noticeId, String noticeNo, String noticeStatus,
                                Long customerId, String customerName, LocalDate shipDate, Long warehouseId, String warehouseName, Long pickerId,
                                String pickerName, LocalDateTime startedAt, LocalDateTime completedAt, String remark, List<PickingLineVO> lines,
                                List<NoticeLineSum> summary, LocalDateTime createdAt) {
    }

    public record PickLineSave(@NotNull Long noticeLineId, String batchNo, Long locationId, BigDecimal suggestedQty, @NotNull BigDecimal pickedQty,
                               String serialNos, Boolean shortage) {
    }

    public record SaveLinesReq(@Valid List<PickLineSave> lines) {
    }

    public record CompleteReq(boolean acceptShort) {
    }

    public record BatchOption(String batchNo, Long locationId, BigDecimal availableQty, LocalDate productionDate, LocalDate expireDate) {
    }
}
