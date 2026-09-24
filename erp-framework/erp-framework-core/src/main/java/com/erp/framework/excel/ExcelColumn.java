package com.erp.framework.excel;

import java.util.List;
import java.util.function.Function;

/**
 * Excel 列定义（导出与导入模板共用）。
 *
 * @param key      字段键（导出时前端可按 key 选择列；导入时读取结果以 key 为键）
 * @param label    列名（中文）
 * @param required 导入必填（模板中列名后带 *）
 * @param hint     模板第二行的填写说明
 * @param type     TEXT / NUMBER / DATE / DATETIME
 * @param options  下拉选项（模板中生成数据验证下拉）
 * @param width    列宽（字符数）
 * @param getter   导出时取值
 */
public record ExcelColumn<T>(String key, String label, boolean required, String hint, Type type, List<String> options,
                             int width, Function<T, Object> getter) {

    public enum Type { TEXT, NUMBER, DATE, DATETIME }

    public static <T> ExcelColumn<T> text(String key, String label, Function<T, Object> getter) {
        return new ExcelColumn<>(key, label, false, null, Type.TEXT, List.of(), 16, getter);
    }

    public static <T> ExcelColumn<T> number(String key, String label, Function<T, Object> getter) {
        return new ExcelColumn<>(key, label, false, null, Type.NUMBER, List.of(), 14, getter);
    }

    public static <T> ExcelColumn<T> date(String key, String label, Function<T, Object> getter) {
        return new ExcelColumn<>(key, label, false, null, Type.DATE, List.of(), 12, getter);
    }

    public static <T> ExcelColumn<T> dateTime(String key, String label, Function<T, Object> getter) {
        return new ExcelColumn<>(key, label, false, null, Type.DATETIME, List.of(), 20, getter);
    }

    /** 导入列（无取值函数） */
    public static <T> ExcelColumn<T> input(String key, String label, boolean required, String hint) {
        return new ExcelColumn<>(key, label, required, hint, Type.TEXT, List.of(), 18, null);
    }

    public ExcelColumn<T> asRequired() {
        return new ExcelColumn<>(key, label, true, hint, type, options, width, getter);
    }

    public ExcelColumn<T> hint(String h) {
        return new ExcelColumn<>(key, label, required, h, type, options, width, getter);
    }

    public ExcelColumn<T> options(List<String> opts) {
        return new ExcelColumn<>(key, label, required, hint, type, opts, width, getter);
    }

    public ExcelColumn<T> ofType(Type t) {
        return new ExcelColumn<>(key, label, required, hint, t, options, width, getter);
    }

    public ExcelColumn<T> width(int w) {
        return new ExcelColumn<>(key, label, required, hint, type, options, w, getter);
    }

    /** 模板中显示的列名：必填带 * */
    public String headerText() {
        return required ? label + "*" : label;
    }
}
