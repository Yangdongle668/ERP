package com.erp.module.crm.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 客户、联系人、地址、银行、转移（需求 03-01） */
public final class CustomerVOs {

    private CustomerVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CustomerQuery extends PageParam {
        /** 编码/名称/英文名/简称 */
        private String keyword;
        /** 逗号分隔；为空默认潜在、审批中、正式 */
        private String statuses;
        private String levels;
        private String countries;
        private Long ownerId;
        private String customerType;
        private String source;
        private LocalDate lastOrderFrom;
        private LocalDate lastOrderTo;
        /** 超过 N 天未下单（从未下单的也算） */
        private Integer noOrderDays;
        /** 导出列 */
        private String columns;
    }

    /** creditLimit：无 crm:customer:credit 权限时为空，creditVisible=false */
    public record CustomerRow(Long id, String code, String shortName, String name, String nameEn, String country, String customerType, String level,
                              Long ownerId, String ownerName, String primaryContact, String primaryContactEmail, String currency,
                              BigDecimal creditLimit, boolean creditVisible, LocalDate lastOrderDate, String customerStatus,
                              LocalDateTime createdAt, int version) {
    }

    /** 选择器（CustomerSelect） */
    public record CustomerBrief(Long id, String code, String name, String shortName, String customerStatus, String currency, Long paymentTermId,
                                String tradeTerm, BigDecimal salesTaxRate, Long ownerId) {
    }

    public record ContactSave(@NotBlank(message = "请填写联系人姓名") @Size(max = 64) String name, String gender, @Size(max = 64) String title,
                              String role, @Size(max = 128) String email, @Size(max = 32) String phone, @Size(max = 32) String mobile,
                              @Size(max = 64) String im, LocalDate birthday, Boolean isPrimary, String status, @Size(max = 256) String remark) {
    }

    public record AddressSave(@NotBlank(message = "请选择地址类型") String addressType, @NotBlank(message = "请填写抬头公司名") @Size(max = 256) String companyName,
                              @Size(max = 64) String contactName, @Size(max = 32) String phone, @NotBlank(message = "请选择地址国家") String country,
                              @Size(max = 64) String province, @Size(max = 64) String city, @Size(max = 16) String zip,
                              @NotBlank(message = "请填写详细地址") @Size(max = 512) String addressLine, Boolean isDefault, @Size(max = 256) String remark) {
    }

    public record BankSave(@NotBlank(message = "请填写开户行") @Size(max = 128) String bankName,
                           @NotBlank(message = "请填写户名") @Size(max = 128) String accountName,
                           @NotBlank(message = "请填写账号") @Size(max = 64) String accountNo, @Size(max = 16) String swift, String currency,
                           @Size(max = 256) String remark) {
    }

    /** contacts / addresses / banks 为 null 时不修改 */
    public record CustomerSave(@Size(max = 32) String code,
                               @NotBlank(message = "请填写客户名称") @Size(max = 128) String name,
                               @Size(max = 256) String nameEn,
                               @Size(max = 32) String shortName,
                               String customerType, String level,
                               @NotBlank(message = "请选择国家") String country,
                               Boolean isForeign,
                               @Size(max = 64) String province, @Size(max = 64) String city, @Size(max = 256) String address,
                               String industry, String source, @Size(max = 128) String website, @Size(max = 64) String phone,
                               @Size(max = 128) String email, @Size(max = 32) String taxNo, Long ownerId,
                               String currency, Long paymentTermId, String tradeTerm, BigDecimal salesTaxRate, Integer creditDays, String creditControl,
                               @Size(max = 1000) String remark,
                               @Valid List<ContactSave> contacts, @Valid List<AddressSave> addresses, @Valid List<BankSave> banks,
                               List<Long> fileIds, Integer version) {
    }

    /** warnings：疑似重复（参数为提示时）、正式客户改名提示等不阻止保存的提示 */
    public record SaveResult(Long id, List<String> warnings) {
    }

    public record ContactResp(Long id, String name, String gender, String title, String role, String email, String phone, String mobile, String im,
                              LocalDate birthday, boolean isPrimary, String status, String remark) {
    }

    public record AddressResp(Long id, String addressType, String companyName, String contactName, String phone, String country, String province,
                              String city, String zip, String addressLine, boolean isDefault, String remark) {
    }

    public record BankResp(Long id, String bankName, String accountName, String accountNo, String swift, String currency, String remark) {
    }

    /** 信用概况（需要 crm:customer:credit 权限） */
    public record CreditSummary(BigDecimal creditLimit, Integer creditDays, String creditControl, BigDecimal receivableBalance, BigDecimal overdueAmount,
                                BigDecimal openOrderAmount, BigDecimal used, BigDecimal available, BigDecimal usagePct) {
    }

    public record CustomerDetail(Long id, String code, String name, String nameEn, String shortName, String customerType, String level,
                                 String customerStatus, boolean isForeign, String country, String province, String city, String address, String industry,
                                 String source, String website, String phone, String email, String taxNo, Long ownerId, String ownerName, Long deptId,
                                 String deptName, String currency, Long paymentTermId, String paymentTermName, String tradeTerm, BigDecimal salesTaxRate,
                                 String blacklistReason, LocalDate firstOrderDate, LocalDate lastOrderDate, String remark,
                                 List<ContactResp> contacts, List<AddressResp> addresses, List<BankResp> banks, CreditSummary credit,
                                 String createdByName, LocalDateTime createdAt, LocalDateTime updatedAt, int version) {
    }

    public record DuplicateCheckReq(Long id, String name, String country, String taxNo, String website) {
    }

    /** matchedBy：NAME 名称 / TAX_NO 税号 / WEBSITE 网址域名 */
    public record DuplicateRow(Long id, String code, String name, String country, String ownerName, String matchedBy) {
    }

    public record TransferReq(@NotEmpty(message = "请选择客户") List<Long> customerIds, @NotNull(message = "请选择新负责人") Long newOwnerId,
                              Boolean transferDocs, @NotBlank(message = "请填写转移原因") @Size(max = 512) String reason) {
    }

    public record TransferLogRow(Long id, Long fromOwnerId, String fromOwnerName, Long toOwnerId, String toOwnerName, boolean transferDocs,
                                 String reason, String operatorName, LocalDateTime createdAt) {
    }

    public record ReasonReq(@Size(max = 512) String reason) {
    }

    /** 转正式结果：ACTIVE 直接生效 / PENDING 已提交审批 */
    public record StatusResult(String customerStatus) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ContactQuery extends PageParam {
        private String name;
        private String email;
        private String phone;
        private Long customerId;
        private String role;
        private String status;
    }

    public record ContactRow(Long id, Long customerId, String customerCode, String customerShortName, String name, String title, String role,
                             String email, String mobile, String phone, boolean isPrimary, String status) {
    }
}
