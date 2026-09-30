package com.erp.module.finance.controller.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 成本核算（12-07） */
public final class CostVOs {

    private CostVOs() {
    }

    public record RunVO(Long id, String period, String status, LocalDateTime startedAt, LocalDateTime finishedAt, String operatorName, String errorMessage,
                        int materialCount, int orderCount, BigDecimal totalCost, int exceptionCount) {
    }

    /**
     * 计算前检查
     *
     * @param canCalculate 库存已月结、财务未结账、未锁定、没有进行中的计算
     */
    public record CheckResult(String period, boolean inventoryClosed, boolean financeClosed, boolean locked, boolean running, boolean expenseEntered,
                              int orderCount, BigDecimal laborTotal, BigDecimal overheadTotal, RunVO lastRun, boolean canCalculate, List<String> messages) {
    }

    public record ExpenseRow(Long deptId, String deptName, BigDecimal workHours, BigDecimal laborAmount, BigDecimal overheadAmount, String remark) {
    }

    public record ExpenseLine(@NotNull Long deptId, BigDecimal laborAmount, BigDecimal overheadAmount, String remark) {
    }

    public record ExpenseSave(@NotBlank String period, List<ExpenseLine> lines) {
    }

    public record ExceptionVO(String type, Long materialId, String materialCode, String materialName, Long prodOrderId, String prodOrderNo, String deptName,
                              String message) {
    }

    public record MaterialCostRow(Long materialId, String materialCode, String materialName, String spec, String uom, BigDecimal openingQty,
                                  BigDecimal openingAmount, BigDecimal inQty, BigDecimal inAmount, BigDecimal unitCost, BigDecimal prevUnitCost,
                                  BigDecimal outQty, BigDecimal outAmount, BigDecimal closingQty, BigDecimal closingAmount) {
    }

    /** 产品成本表：标准成本未维护时差异为空 */
    public record ProductCostRow(Long materialId, String materialCode, String materialName, BigDecimal finishedQty, BigDecimal unitCost,
                                 BigDecimal materialCost, BigDecimal laborCost, BigDecimal overheadCost, BigDecimal totalCost, BigDecimal standardCost,
                                 BigDecimal diff, BigDecimal diffRate, int orderCount) {
    }

    public record OrderMaterialVO(Long materialId, String materialCode, String materialName, BigDecimal issueQty, BigDecimal returnQty, BigDecimal unitCost,
                                  BigDecimal amount) {
    }

    public record OrderCostVO(String period, Long prodOrderId, String prodOrderNo, Long materialId, String materialCode, String materialName, String deptName,
                              BigDecimal workHours, BigDecimal openingWip, BigDecimal materialCost, BigDecimal laborCost, BigDecimal overheadCost,
                              BigDecimal totalCost, BigDecimal finishedQty, BigDecimal finishedCost, BigDecimal endingWip, BigDecimal unitCost,
                              List<OrderMaterialVO> materials) {
    }
}
