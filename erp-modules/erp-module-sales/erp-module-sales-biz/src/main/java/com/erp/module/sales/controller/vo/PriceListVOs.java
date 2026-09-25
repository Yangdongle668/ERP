package com.erp.module.sales.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 销售价格表（需求 04-01） */
public final class PriceListVOs {

    private PriceListVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PriceListQuery extends PageParam {
        private String keyword;
        private String scope;
        private Long customerId;
        private String customerLevel;
        private String currency;
        /** 逗号分隔；为空默认已审核 */
        private String statuses;
        /** 包含该物料 */
        private Long materialId;
        /** 该日期有效 */
        private LocalDate effectiveOn;
        private String columns;
    }

    public record PriceListRow(Long id, String docNo, String name, String scope, Long customerId, String customerName, String customerLevel,
                               String currency, boolean taxIncluded, LocalDate effectiveFrom, LocalDate effectiveTo, int itemCount, String status,
                               String ownerName, LocalDateTime createdAt) {
    }

    public record ItemSave(@NotNull(message = "请选择物料") Long materialId, @NotBlank(message = "请选择单位") String uom,
                           BigDecimal minQty, @NotNull(message = "请填写单价") BigDecimal price, @Size(max = 256) String remark) {
    }

    public record PriceListSave(@NotBlank(message = "请填写名称") @Size(max = 64) String name,
                                @NotBlank(message = "请选择适用范围") String scope, Long customerId, String customerLevel,
                                @NotBlank(message = "请选择币别") String currency, Boolean taxIncluded,
                                @NotNull(message = "请选择生效日期") LocalDate effectiveFrom, LocalDate effectiveTo,
                                @Size(max = 1000) String remark, @Valid List<ItemSave> items, Integer version) {
    }

    /** costPrice、marginRate 需要 sales:order:cost 字段权限 */
    public record ItemResp(Long id, int lineNo, Long materialId, String materialCode, String materialName, String materialSpec, String baseUom,
                           String uom, BigDecimal minQty, BigDecimal price, BigDecimal costPrice, BigDecimal marginRate, boolean belowFloor,
                           String remark) {
    }

    public record PriceListDetail(Long id, String docNo, String name, String scope, Long customerId, String customerName, String customerLevel,
                                  String currency, boolean taxIncluded, LocalDate effectiveFrom, LocalDate effectiveTo, String status,
                                  String closeReason, String remark, boolean costVisible, String ownerName, LocalDateTime createdAt, int version,
                                  List<ItemResp> items) {
    }

    /** 导出明细行 */
    public record ItemExportRow(String docNo, String name, String scope, String customerName, String currency, boolean taxIncluded,
                                LocalDate effectiveFrom, LocalDate effectiveTo, String status, String materialCode, String materialName,
                                String uom, BigDecimal minQty, BigDecimal price) {
    }
}
