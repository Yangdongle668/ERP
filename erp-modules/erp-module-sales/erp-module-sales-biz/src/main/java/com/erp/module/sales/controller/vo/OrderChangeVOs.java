package com.erp.module.sales.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.sales.controller.vo.OrderVOs.OrderLineResp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 销售订单变更（需求 04-04） */
public final class OrderChangeVOs {

    private OrderChangeVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChangeQuery extends PageParam {
        private String docNo;
        private String orderNo;
        private Long customerId;
        private String changeReason;
        private String statuses;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record ChangeRow(Long id, String docNo, Long orderId, String orderNo, Long customerId, String customerName, int versionFrom, int versionTo,
                            String changeReason, String currency, BigDecimal amountDiff, BigDecimal amountChangeBase, String status, String ownerName,
                            LocalDate docDate) {
    }

    /** 单头可变更的字段（与订单当前值比较得出变更项） */
    public record HeaderSave(@Size(max = 64) String customerPoNo, Long paymentTermId, String tradeTerm, @Size(max = 64) String portOfLoading,
                             @Size(max = 64) String portOfDestination, Long shipToAddressId, Long contactId, @Size(max = 2000) String terms,
                             @Size(max = 1000) String remark) {
    }

    /**
     * @param changeType ADD / MODIFY / CANCEL
     * @param newQty     销售单位数量（ADD 时的单位为 uom）
     * @param newPrice   按订单“价格含税”口径的单价
     */
    public record LineSave(@NotBlank(message = "请选择变更类型") String changeType, Long orderLineId, Long materialId, String uom, BigDecimal newQty,
                           BigDecimal newPrice, LocalDate newRequiredDate, @Size(max = 64) String newCustomerPartNo,
                           @Size(max = 512) String newDescription, @Size(max = 256) String remark) {
    }

    public record ChangeSave(@NotBlank(message = "请选择变更原因") String changeReason,
                             @NotBlank(message = "请填写原因说明") @Size(max = 512) String reasonRemark,
                             @Valid HeaderSave header, @Valid List<LineSave> lines, Integer version) {
    }

    public record HeaderChange(String field, String label, String oldValue, String newValue) {
    }

    public record ChangeLineResp(Long id, int lineNo, String changeType, Long orderLineId, Integer orderLineNo, Long materialId, String materialCode,
                                 String materialName, String materialSpec, String uom, BigDecimal oldQty, BigDecimal newQty, BigDecimal oldPrice,
                                 BigDecimal newPrice, LocalDate oldRequiredDate, LocalDate newRequiredDate, String newCustomerPartNo,
                                 String newDescription, String remark) {
    }

    /**
     * @param orderLines   订单当前行（编辑页的“当前值”）
     * @param header       变更后的单头（未变更的字段为订单当前值）
     */
    public record ChangeDetail(Long id, String docNo, LocalDate docDate, String status, Long orderId, String orderNo, int orderVersion,
                               Long customerId, String customerName, String currency, boolean taxIncluded, int orderVersionFrom, String changeReason,
                               String reasonRemark, BigDecimal amountBefore, BigDecimal amountAfter, BigDecimal amountChangeBase,
                               List<HeaderChange> headerChanges, HeaderSave header, Long ownerId, String ownerName, String createdByName,
                               LocalDateTime createdAt, int version, List<ChangeLineResp> lines, List<OrderLineResp> orderLines) {
    }
}
