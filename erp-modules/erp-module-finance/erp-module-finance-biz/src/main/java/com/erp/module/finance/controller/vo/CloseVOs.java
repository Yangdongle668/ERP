package com.erp.module.finance.controller.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 月结（12-09） */
public final class CloseVOs {

    private CloseVOs() {
    }

    /** 期间列表：status NOT_OPEN/OPEN/CLOSED */
    public record PeriodRow(String period, LocalDate startDate, LocalDate endDate, String status, boolean costLocked, boolean fxDone,
                            String closedByName, LocalDateTime closedAt, boolean canClose, boolean canReopen) {
    }

    /** 检查项：blocking = 未通过时阻止结账；route 为处理页面 */
    public record CheckItem(String key, String label, boolean passed, boolean blocking, String message, long count, String route) {
    }

    public record CheckResult(String period, String status, boolean passed, List<CheckItem> items) {
    }

    /** 外币重估明细：rate 为空表示缺少月末汇率 */
    public record FxRow(String docType, Long docId, String docNo, String partnerName, String currency, BigDecimal fcBalance, BigDecimal bookBase,
                        BigDecimal periodEndRate, BigDecimal revaluedBase, BigDecimal diff) {
    }

    public record FxResult(String period, boolean done, List<FxRow> rows, BigDecimal totalDiff, List<String> missingRates, Long voucherId, String voucherNo) {
    }

    public record ReopenReq(String reason) {
    }
}
