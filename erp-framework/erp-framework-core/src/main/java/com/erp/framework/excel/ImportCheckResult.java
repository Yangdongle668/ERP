package com.erp.framework.excel;

import java.util.List;
import java.util.Map;

/**
 * 导入校验结果（前端 ImportDialog 使用）。
 *
 * @param columns 预览列
 * @param rows    每行数据与错误；action 为 CREATE / UPDATE / OVERWRITE 等，可为空
 */
public record ImportCheckResult(int total, int errorCount, List<Column> columns, List<Row> rows) {

    public record Column(String key, String label) {
    }

    public record Row(int rowNo, Map<String, String> data, List<String> errors, String action) {
    }

    public static ImportCheckResult of(List<? extends ExcelColumn<?>> columns, List<ImportRow> rows, java.util.function.Function<ImportRow, String> action) {
        List<Column> cols = columns.stream().map(c -> new Column(c.key(), c.label())).toList();
        List<Row> list = rows.stream().map(r -> new Row(r.rowNo(), r.data(), List.copyOf(r.errors()), action == null ? null : action.apply(r))).toList();
        int errors = (int) rows.stream().filter(ImportRow::hasError).count();
        return new ImportCheckResult(rows.size(), errors, cols, list);
    }
}
