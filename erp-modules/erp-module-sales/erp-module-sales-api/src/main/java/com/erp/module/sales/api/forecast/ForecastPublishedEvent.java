package com.erp.module.sales.api.forecast;

import com.erp.common.event.DomainEvent;

import java.util.List;

/** 预测发布 / 关闭 / 冲销变化（PMC 维护预测需求）。action：PUBLISHED / CLOSED / CONSUMED */
public class ForecastPublishedEvent extends DomainEvent {

    public static final String PUBLISHED = "PUBLISHED";
    public static final String CLOSED = "CLOSED";
    public static final String CONSUMED = "CONSUMED";

    private final Long forecastId;
    private final String forecastNo;
    private final String action;
    private final List<Long> materialIds;

    public ForecastPublishedEvent(Long forecastId, String forecastNo, String action, List<Long> materialIds) {
        this.forecastId = forecastId;
        this.forecastNo = forecastNo;
        this.action = action;
        this.materialIds = List.copyOf(materialIds);
    }

    public Long getForecastId() { return forecastId; }
    public String getForecastNo() { return forecastNo; }
    public String getAction() { return action; }
    public List<Long> getMaterialIds() { return materialIds; }
}
