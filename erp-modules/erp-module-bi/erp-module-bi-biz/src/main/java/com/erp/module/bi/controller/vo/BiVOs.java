package com.erp.module.bi.controller.vo;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** BI 页面接口的请求与响应 */
public final class BiVOs {

    private BiVOs() {
    }

    public record DimensionVO(String code, String label) {
    }

    /**
     * 指标库行。name 为展示名称（未设置时为代码注册名称），defaultName 为代码注册名称。
     */
    public record MetricVO(String code, String name, String defaultName, String topic, String unit, String description, String source,
                           List<DimensionVO> dimensions, String permission, boolean sensitive, boolean derived, String remark, String ownerName,
                           LocalDateTime lastCalculatedAt) {
    }

    public record MetricUpdate(@Size(max = 64) String displayName, @Size(max = 1000) String description, @Size(max = 64) String ownerName) {
    }

    /** 专题页配置（GET /bi/pages/{code}/config）：本专题当前用户可用的指标与维度 */
    public record PageConfig(String code, String name, List<MetricVO> metrics, List<DimensionVO> dimensions, LocalDateTime dataUpdatedAt) {
    }
}
