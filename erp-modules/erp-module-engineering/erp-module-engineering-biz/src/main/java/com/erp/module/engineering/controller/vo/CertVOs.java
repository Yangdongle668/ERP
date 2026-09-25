package com.erp.module.engineering.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/** 认证证书（需求 05-09） */
public final class CertVOs {

    private CertVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CertQuery extends PageParam {
        private String certNo;
        private String certType;
        private Long materialId;
        /** VALID 有效 / EXPIRING 90 天内到期 / EXPIRED 已过期 / REVOKED 已撤销 */
        private String validity;
        private LocalDate expireFrom;
        private LocalDate expireTo;
    }

    /**
     * @param validity VALID / LONG_TERM / EXPIRING / EXPIRED / REVOKED（由到期日计算）
     */
    public record CertRow(Long id, String certType, String certNo, String name, String issuingBody, String holder, LocalDate issueDate,
                          LocalDate expireDate, List<String> countries, String scope, String certStatus, String validity, Long daysLeft,
                          List<MaterialRef> materials, int fileCount, String revokeReason, String remark, int version) {
    }

    public record MaterialRef(Long id, String code, String name) {
    }

    public record CertSave(@NotBlank(message = "请选择认证类型") String certType,
                           @NotBlank(message = "请输入证书编号") @Size(max = 64) String certNo,
                           @NotBlank(message = "请输入证书名称") @Size(max = 128) String name,
                           @NotBlank(message = "请输入发证机构") @Size(max = 128) String issuingBody,
                           @Size(max = 128) String holder,
                           @NotNull(message = "请选择发证日期") LocalDate issueDate,
                           LocalDate expireDate, List<String> countries, @Size(max = 1000) String scope, List<Long> materialIds,
                           List<Long> fileIds, @Size(max = 512) String remark, Integer version) {
    }
}
