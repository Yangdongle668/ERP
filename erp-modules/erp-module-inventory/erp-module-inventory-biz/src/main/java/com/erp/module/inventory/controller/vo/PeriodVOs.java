package com.erp.module.inventory.controller.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 期初与月结接口的请求/响应（需求 08-09） */
public final class PeriodVOs {

    private PeriodVOs() {
    }

    public record PeriodRow(Long id, String period, LocalDate startDate, LocalDate endDate, String periodStatus, boolean opening,
                            String closedByName, LocalDateTime closedAt, boolean financeClosed, boolean canClose, boolean canReopen) {
    }

    public record InitReq(@NotBlank(message = "请选择启用期间") @Pattern(regexp = "^\\d{4}(0[1-9]|1[0-2])$", message = "期间格式为 yyyyMM") String period) {
    }

    /** 期初状态：未设置启用期间时 period 为空 */
    public record OpeningInfo(String period, LocalDate startDate, LocalDate openingDate, boolean completed, int docCount, int lineCount,
                              BigDecimal totalAmount, boolean canClear) {
    }

    /** 月结检查项：level = BLOCK 阻止 / WARN 警告 */
    public record CheckItem(String level, String title, List<String> details) {
    }

    public record CheckResult(String period, boolean passed, List<CheckItem> items) {
    }
}
