package com.erp.module.bi.service.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 通用查询结果。rows 中维度列为原始值（ID 或编码）并附带 {@code <维度>_label} 名称列，指标列为数值（比率为百分数）。
 *
 * @param metrics       所用指标的口径（“查看口径”）
 * @param dataUpdatedAt 数据更新于（最近一次成功的数据任务）
 */
public record BiQueryResult(List<Column> columns, List<Map<String, Object>> rows, List<MetricMeta> metrics, LocalDate from, LocalDate to,
                            String granularity, boolean truncated, LocalDateTime dataUpdatedAt) {

    /** kind：DIMENSION / METRIC；unit：指标单位（AMOUNT / PERCENT / QTY / COUNT / DAYS / PRICE） */
    public record Column(String key, String label, String kind, String unit) {
    }

    public record MetricMeta(String code, String name, String unit, String description, String source, boolean sensitive) {
    }
}
