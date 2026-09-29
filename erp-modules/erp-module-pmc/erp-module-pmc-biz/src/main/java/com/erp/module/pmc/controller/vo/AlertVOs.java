package com.erp.module.pmc.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 交期预警（需求 06-07） */
public final class AlertVOs {

    private AlertVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AlertQuery extends PageParam {
        /** INFO / WARNING / CRITICAL，逗号分隔 */
        private String levels;
        /** NO_STOCK_NO_WO / MATERIAL_SHORTAGE / CAPACITY / WO_DELAY */
        private String cause;
        private Long customerId;
        private Long ownerId;
        private Long materialId;
        /** OPEN / HANDLED / IGNORED，逗号分隔；为空默认未处理 */
        private String statuses;
    }

    public record AlertRow(Long id, Long orderId, String orderNo, int lineNo, Long orderLineId, Long customerId, String customerName, Long materialId,
                           String materialCode, String materialName, BigDecimal openQty, LocalDate promisedDate, LocalDate estimatedDate, int delayDays,
                           String alertLevel, String cause, String causeDetail, Long ownerId, String ownerName, String handleStatus, String handleRemark,
                           String handledByName, LocalDateTime handledAt, LocalDateTime calculatedAt) {
    }

    public record Summary(long critical, long warning, long info) {
    }

    public record HandleReq(@Size(max = 512) String remark) {
    }

    public record RecalcResult(int lineCount, int alertCount, int raised, int resolved) {
    }
}
