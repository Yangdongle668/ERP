package com.erp.module.purchase.api.statement;

import com.erp.common.event.DomainEvent;

/**
 * 对账单取消确认前同步发布（PUR-ST-R05）：财务已根据此对账单生成应付单时，监听方抛出 BizException
 * （提示“财务已根据此对账单生成应付，不能取消确认”）阻止取消。
 */
public class PurchaseStatementUnconfirmingEvent extends DomainEvent {

    private final Long statementId;
    private final String statementNo;

    public PurchaseStatementUnconfirmingEvent(Long statementId, String statementNo) {
        this.statementId = statementId;
        this.statementNo = statementNo;
    }

    public Long getStatementId() { return statementId; }
    public String getStatementNo() { return statementNo; }
}
