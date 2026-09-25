package com.erp.module.engineering.api.cert;

import com.erp.common.event.DomainEvent;

import java.time.LocalDate;

/** 证书即将到期（到期前 N 天）或已过期当天 */
public class CertificationExpiringEvent extends DomainEvent {

    private final Long certificationId;
    private final String certType;
    private final String certNo;
    private final LocalDate expireDate;
    private final long daysLeft;

    public CertificationExpiringEvent(Long certificationId, String certType, String certNo, LocalDate expireDate, long daysLeft) {
        this.certificationId = certificationId;
        this.certType = certType;
        this.certNo = certNo;
        this.expireDate = expireDate;
        this.daysLeft = daysLeft;
    }

    public Long getCertificationId() { return certificationId; }
    public String getCertType() { return certType; }
    public String getCertNo() { return certNo; }
    public LocalDate getExpireDate() { return expireDate; }
    public long getDaysLeft() { return daysLeft; }
}
