package com.erp.framework.excel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 导入文件中的一行。
 *
 * @param rowNo  Excel 行号（从 1 开始，与用户在 Excel 中看到的一致）
 * @param data   列 key → 单元格文本（已去首尾空格；日期为 yyyy-MM-dd；数字为不带千分位的文本）
 * @param errors 校验错误，调用方追加
 */
public record ImportRow(int rowNo, Map<String, String> data, List<String> errors) {

    public ImportRow(int rowNo, Map<String, String> data) {
        this(rowNo, data, new ArrayList<>());
    }

    public String get(String key) {
        String v = data.get(key);
        return v == null || v.isEmpty() ? null : v;
    }

    public void error(String message) {
        errors.add(message);
    }

    public boolean hasError() {
        return !errors.isEmpty();
    }
}
