package com.erp.module.engineering.api.tooling;

import com.erp.common.event.DomainEvent;

/** 工装使用次数达到设计寿命的预警比例（每个工装只提醒一次，登记报废或调整次数后重置） */
public class ToolingLifeWarningEvent extends DomainEvent {

    private final Long toolingId;
    private final String code;
    private final int usedCount;
    private final int designLife;

    public ToolingLifeWarningEvent(Long toolingId, String code, int usedCount, int designLife) {
        this.toolingId = toolingId;
        this.code = code;
        this.usedCount = usedCount;
        this.designLife = designLife;
    }

    public Long getToolingId() { return toolingId; }
    public String getCode() { return code; }
    public int getUsedCount() { return usedCount; }
    public int getDesignLife() { return designLife; }
}
