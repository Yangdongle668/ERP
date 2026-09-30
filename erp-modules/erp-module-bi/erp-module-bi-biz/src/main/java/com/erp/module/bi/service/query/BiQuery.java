package com.erp.module.bi.service.query;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 通用查询请求（需求 13-01 第 6 节 POST /bi/query）。
 *
 * @param metrics     指标编码（1～12 个）
 * @param dimensions  维度编码（0～3 个），如 date、customer、category
 * @param filters     维度 → 取值列表（IN）；类别包含下级类别
 * @param from        开始日期（含），为空取本月 1 日
 * @param to          结束日期（含），为空取今天
 * @param granularity 时间维度粒度 day / month / quarter / year，默认 month；月度来源的指标按 day 查询时按月
 * @param sort        排序列（维度或指标编码），为空时有日期维度按日期升序，否则按第一个指标降序
 * @param order       asc / desc
 * @param limit       返回行数上限，默认 500，最大 5000
 */
public record BiQuery(List<String> metrics, List<String> dimensions, Map<String, List<String>> filters, LocalDate from, LocalDate to,
                      String granularity, String sort, String order, Integer limit) {

    public static BiQuery of(List<String> metrics, List<String> dimensions, LocalDate from, LocalDate to, String granularity) {
        return new BiQuery(metrics, dimensions, null, from, to, granularity, null, null, null);
    }

    public BiQuery withFilters(Map<String, List<String>> f) {
        return new BiQuery(metrics, dimensions, f, from, to, granularity, sort, order, limit);
    }

    public BiQuery withSort(String s, String o, Integer l) {
        return new BiQuery(metrics, dimensions, filters, from, to, granularity, s, o, l);
    }

    public BiQuery withRange(LocalDate f, LocalDate t) {
        return new BiQuery(metrics, dimensions, filters, f, t, granularity, sort, order, limit);
    }
}
