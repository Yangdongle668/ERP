package com.erp.module.engineering.api.cert;

import java.time.LocalDate;
import java.util.List;

/** 认证证书；expireDate 为空表示长期有效 */
public record CertificationDTO(Long id, String certType, String certNo, String name, String issuingBody, LocalDate issueDate,
                               LocalDate expireDate, List<String> countries) {
}
