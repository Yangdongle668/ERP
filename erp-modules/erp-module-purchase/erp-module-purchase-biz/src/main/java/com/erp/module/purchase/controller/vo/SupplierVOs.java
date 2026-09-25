package com.erp.module.purchase.controller.vo;

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

/** 供应商（需求 07-01） */
public final class SupplierVOs {

    private SupplierVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SupplierQuery extends PageParam {
        /** 编码前缀 / 名称 / 简称 */
        private String keyword;
        /** 状态，逗号分隔 */
        private String statuses;
        private String level;
        private String supplierType;
        private Long buyerId;
        /** 资质到期：EXPIRED 已过期 / SOON 30 天内到期 */
        private String certExpiry;
        /** 可供物料 */
        private Long materialId;
        /** 导出列 */
        private String columns;
    }

    public record SupplierRow(Long id, String code, String shortName, String name, String supplierType, String level, String country,
                              Long buyerId, String buyerName, String currency, Long paymentTermId, String paymentTermName,
                              String primaryContact, String primaryPhone, boolean certExpired, boolean certExpiring, String status,
                              LocalDate qualifiedAt, LocalDateTime updatedAt) {
    }

    public record ContactSave(@NotBlank(message = "请填写联系人姓名") @Size(max = 64) String name, @Size(max = 64) String title,
                              @Size(max = 32) String role, @Size(max = 32) String phone, @Size(max = 32) String mobile,
                              @Size(max = 128) String email, Boolean isPrimary) {
    }

    public record BankSave(@NotBlank(message = "请填写开户行") @Size(max = 128) String bankName,
                           @NotBlank(message = "请填写账户名") @Size(max = 128) String accountName,
                           @NotBlank(message = "请填写账号") @Size(max = 64) String accountNo, @Size(max = 32) String swift,
                           String currency, Boolean isDefault) {
    }

    public record CertSave(@NotBlank(message = "请选择资质类型") String certType, @Size(max = 64) String certNo, LocalDate issueDate,
                           LocalDate expireDate, @NotNull(message = "请上传证书文件") Long fileId, @Size(max = 256) String remark) {
    }

    public record MaterialSave(@NotNull(message = "请选择物料") Long materialId, @Size(max = 64) String supplierPartNo, String supplyStatus,
                               Boolean isDefault, Integer leadTimeDays, BigDecimal moq, BigDecimal mpq, BigDecimal quotaPct,
                               LocalDate approvedAt, @Size(max = 256) String remark) {
    }

    /** contacts / banks / certs / materials 为 null 时不修改 */
    public record SupplierSave(@Size(max = 32) String code, @NotBlank(message = "请填写供应商名称") @Size(max = 128) String name,
                               @Size(max = 256) String nameEn, @NotBlank(message = "请填写简称") @Size(max = 32) String shortName,
                               String supplierType, String level, String country, @Size(max = 64) String province, @Size(max = 64) String city,
                               @Size(max = 256) String address, @Size(max = 32) String taxNo, @Size(max = 32) String phone,
                               @Size(max = 128) String email, @Size(max = 128) String website, Long buyerId, Long deptId,
                               String currency, @NotNull(message = "请选择付款条件") Long paymentTermId, String tradeTerm,
                               BigDecimal purchaseTaxRate, String invoiceType, Integer leadTimeDays, @Size(max = 1000) String remark,
                               @Valid List<ContactSave> contacts, @Valid List<BankSave> banks, @Valid List<CertSave> certs,
                               @Valid List<MaterialSave> materials, List<Long> fileIds, Integer version) {
    }

    public record ContactResp(Long id, String name, String title, String role, String phone, String mobile, String email, boolean isPrimary) {
    }

    public record BankResp(Long id, String bankName, String accountName, String accountNo, String swift, String currency, boolean isDefault) {
    }

    /** @param certStatus VALID / EXPIRING（30 天内）/ EXPIRED */
    public record CertResp(Long id, String certType, String certNo, LocalDate issueDate, LocalDate expireDate, Long fileId, String fileName,
                           String remark, String certStatus) {
    }

    public record SupplierMaterialResp(Long id, Long supplierId, String supplierCode, String supplierName, String supplierStatus,
                                       Long materialId, String materialCode, String materialName, String materialSpec, String baseUom,
                                       String supplierPartNo, String supplyStatus, boolean isDefault, Integer leadTimeDays, BigDecimal moq,
                                       BigDecimal mpq, BigDecimal quotaPct, LocalDate approvedAt, String remark) {
    }

    public record SupplierDetail(Long id, String code, String name, String nameEn, String shortName, String supplierType, String level,
                                 String status, String country, String province, String city, String address, String taxNo, String phone,
                                 String email, String website, Long buyerId, String buyerName, Long deptId, String deptName, String currency,
                                 Long paymentTermId, String paymentTermName, String tradeTerm, BigDecimal purchaseTaxRate, String invoiceType,
                                 Integer leadTimeDays, LocalDate qualifiedAt, String suspendReason, String remark, List<ContactResp> contacts,
                                 List<BankResp> banks, List<CertResp> certs, List<SupplierMaterialResp> materials, int openOrderCount,
                                 String createdByName, LocalDateTime createdAt, LocalDateTime updatedAt, Integer version) {
    }

    /** 选择器（SupplierSelect）：带出默认币别、付款条件、税率、采购员 */
    public record SupplierBrief(Long id, String code, String name, String shortName, String status, String currency, Long paymentTermId,
                                BigDecimal taxRate, Long buyerId, String level) {
    }

    /** 暂停、淘汰结果：未完成采购订单数（提示） */
    public record StatusResult(String status, int openOrderCount) {
    }

    /** 到货与质量：最近 12 个月 */
    public record QualitySummary(int lotCount, int passCount, BigDecimal passRate, List<QualityLot> lots) {
    }

    public record QualityLot(Long receiptId, String receiptNo, LocalDate arrivalDate, String materialCode, String materialName, BigDecimal qty,
                             String inspectStatus, BigDecimal qualifiedQty, BigDecimal rejectedQty, String inspectionNo) {
    }

    public record ReasonReq(String reason) {
    }
}
