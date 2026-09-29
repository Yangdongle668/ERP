package com.erp.module.pmc.controller.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 产能日历、负荷分析、排产（需求 06-05） */
public final class ScheduleVOs {

    private ScheduleVOs() {
    }

    // ==================== 产能日历 ====================

    /** 某天的产能：exception 为例外日（节假日、加班、停机）；loadHours 为已排负荷 */
    public record CalendarDay(LocalDate date, BigDecimal availableHours, BigDecimal defaultHours, boolean exception, String reason,
                              BigDecimal loadHours, BigDecimal loadRate) {
    }

    /** @param workCenterId 0 表示全厂 */
    public record CalendarMonth(Long workCenterId, String workCenterName, String month, List<CalendarDay> days) {
    }

    /** hours 为空表示取消例外（恢复默认） */
    public record CalendarSave(@NotNull Long workCenterId, @NotNull LocalDate date, BigDecimal hours, @Size(max = 64) String reason) {
    }

    public record CalendarBatch(@NotNull Long workCenterId, @NotNull LocalDate from, @NotNull LocalDate to, BigDecimal hours,
                                @Size(max = 64) String reason) {
    }

    // ==================== 负荷分析 ====================

    public record LoadCell(LocalDate date, BigDecimal loadHours, BigDecimal capacityHours, BigDecimal loadRate, boolean overloaded) {
    }

    public record LoadRow(Long workCenterId, String workCenterCode, String workCenterName, Long deptId, BigDecimal totalLoad, BigDecimal totalCapacity,
                          List<LoadCell> cells) {
    }

    public record LoadDetail(Long prodOrderId, String prodOrderNo, String materialCode, String materialName, int operationSeq, String operation,
                             BigDecimal hours, boolean scheduled) {
    }

    // ==================== 排产 ====================

    public record RunReq(Long deptId, String mode) {
    }

    public record RunResult(int orderCount, int operationCount, int lateCount, String mode) {
    }

    /** 甘特图的一个工序块 */
    public record ScheduleRow(Long id, Long prodOrderId, String prodOrderNo, Long materialId, String materialCode, String materialName, BigDecimal qty,
                              int operationSeq, String operation, Long workCenterId, String workCenterName, Long deptId, LocalDateTime schedStart,
                              LocalDateTime schedEnd, BigDecimal loadHours, LocalDate dueDate, boolean late, int priority, boolean locked, boolean manual,
                              boolean applied, String prodStatus) {
    }

    /** 拖拽：移到某工作中心、某天开始 */
    public record AdjustReq(Long workCenterId, @NotNull LocalDate startDate) {
    }

    /** 插单模拟：订单与新优先级（1 最高） */
    public record SimulateReq(@NotNull Long prodOrderId, Integer priority) {
    }

    public record SimulateRow(Long prodOrderId, String prodOrderNo, String materialCode, LocalDate oldEnd, LocalDate newEnd, int delayDays,
                              LocalDate dueDate, boolean lateAfter) {
    }

    public record SimulateResult(Long prodOrderId, String prodOrderNo, LocalDate newEnd, List<SimulateRow> delayed) {
    }

    /** 应用到生产订单的变化 */
    public record ApplyRow(Long prodOrderId, String prodOrderNo, String materialCode, LocalDate planStart, LocalDate planEnd, LocalDate newStart,
                           LocalDate newEnd, String prodStatus) {
    }

    public record ApplyReq(List<Long> prodOrderIds) {
    }
}
