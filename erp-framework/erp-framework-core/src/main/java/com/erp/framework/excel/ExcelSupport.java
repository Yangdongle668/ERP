package com.erp.framework.excel;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel 导入导出（UI 设计规范第 9 节）：
 * <ul>
 *   <li>导出：文件名 {@code {名称}_{yyyyMMddHHmmss}.xlsx}；数字列为数字格式；列可按前端列设置裁剪；</li>
 *   <li>模板：第一行列名（必填带 *），第二行填写说明（灰色斜体，导入时忽略），下拉列生成数据验证；</li>
 *   <li>读取：按列名匹配（忽略 *），最多 N 行；</li>
 *   <li>错误报告：原文件末尾增加“错误原因”列，错误行标红。</li>
 * </ul>
 */
public final class ExcelSupport {

    public static final int MAX_IMPORT_ROWS = 5000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelSupport() {
    }

    // ==================== 导出 ====================

    /**
     * 导出到响应。columnKeys 非空时只导出这些列并按其顺序（与前端列设置一致）。
     */
    public static <T> void export(HttpServletResponse response, String name, List<ExcelColumn<T>> columns, List<T> rows,
                                  List<String> columnKeys) throws IOException {
        prepare(response, fileName(name));
        write(response.getOutputStream(), name, columns, rows, columnKeys);
    }

    /** 生成导出文件内容（后台导出任务使用：结果保存到任务中心） */
    public static <T> byte[] toBytes(String name, List<ExcelColumn<T>> columns, List<T> rows, List<String> columnKeys) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            write(out, name, columns, rows, columnKeys);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** 导出文件名：名称_yyyyMMddHHmmss.xlsx */
    public static String fileName(String name) {
        return name + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
    }

    /**
     * 数据量超过同步导出上限、已转为后台任务时的响应（UI 设计规范 9.2）：
     * {@code {"code":0,"data":{"async":true,"taskId":"…"}}}，前端提示到任务中心下载。
     */
    public static void writeAsyncAccepted(HttpServletResponse response, Long taskId) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":0,\"msg\":\"已转为后台导出\",\"data\":{\"async\":true,\"taskId\":\"" + taskId + "\"}}");
    }

    private static <T> void write(OutputStream target, String name, List<ExcelColumn<T>> columns, List<T> rows,
                                  List<String> columnKeys) throws IOException {
        List<ExcelColumn<T>> cols = selectColumns(columns, columnKeys);
        try (SXSSFWorkbook wb = new SXSSFWorkbook(500)) {
            Sheet sheet = wb.createSheet(safeSheetName(name));
            CellStyle header = headerStyle(wb);
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
            CellStyle dateTimeStyle = wb.createCellStyle();
            dateTimeStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            Row head = sheet.createRow(0);
            for (int i = 0; i < cols.size(); i++) {
                Cell c = head.createCell(i);
                c.setCellValue(cols.get(i).label());
                c.setCellStyle(header);
                sheet.setColumnWidth(i, Math.min(cols.get(i).width(), 60) * 256);
            }
            int r = 1;
            for (T row : rows) {
                Row line = sheet.createRow(r++);
                for (int i = 0; i < cols.size(); i++) {
                    Object v = cols.get(i).getter() == null ? null : cols.get(i).getter().apply(row);
                    writeCell(line.createCell(i), v, dateStyle, dateTimeStyle);
                }
            }
            sheet.createFreezePane(0, 1);
            wb.write(target);
            wb.dispose();
        }
    }

    private static <T> List<ExcelColumn<T>> selectColumns(List<ExcelColumn<T>> columns, List<String> keys) {
        if (keys == null || keys.isEmpty()) return columns;
        Map<String, ExcelColumn<T>> byKey = new LinkedHashMap<>();
        columns.forEach(c -> byKey.put(c.key(), c));
        List<ExcelColumn<T>> result = new ArrayList<>();
        for (String k : keys) {
            ExcelColumn<T> c = byKey.get(k);
            if (c != null) result.add(c);
        }
        return result.isEmpty() ? columns : result;
    }

    private static void writeCell(Cell cell, Object v, CellStyle dateStyle, CellStyle dateTimeStyle) {
        if (v == null) return;
        if (v instanceof BigDecimal b) cell.setCellValue(b.doubleValue());
        else if (v instanceof Number n) cell.setCellValue(n.doubleValue());
        else if (v instanceof LocalDate d) {
            cell.setCellValue(d);
            cell.setCellStyle(dateStyle);
        } else if (v instanceof LocalDateTime dt) {
            cell.setCellValue(dt);
            cell.setCellStyle(dateTimeStyle);
        } else if (v instanceof Boolean b) cell.setCellValue(b ? "是" : "否");
        else cell.setCellValue(String.valueOf(v));
    }

    // ==================== 模板 ====================

    public static void template(HttpServletResponse response, String name, List<? extends ExcelColumn<?>> columns) throws IOException {
        prepare(response, name + "导入模板.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(safeSheetName(name));
            CellStyle header = headerStyle(wb);
            CellStyle requiredHeader = headerStyle(wb);
            Font red = wb.createFont();
            red.setBold(true);
            red.setColor(IndexedColors.RED.getIndex());
            requiredHeader.setFont(red);
            CellStyle hintStyle = wb.createCellStyle();
            Font hintFont = wb.createFont();
            hintFont.setItalic(true);
            hintFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            hintStyle.setFont(hintFont);
            hintStyle.setWrapText(true);
            CellStyle textFormat = wb.createCellStyle();
            textFormat.setDataFormat(wb.createDataFormat().getFormat("@"));
            CellStyle dateFormat = wb.createCellStyle();
            dateFormat.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));

            Row head = sheet.createRow(0);
            Row hint = sheet.createRow(1);
            DataValidationHelper dv = sheet.getDataValidationHelper();
            for (int i = 0; i < columns.size(); i++) {
                ExcelColumn<?> col = columns.get(i);
                Cell c = head.createCell(i);
                c.setCellValue(col.headerText());
                c.setCellStyle(col.required() ? requiredHeader : header);
                Cell h = hint.createCell(i);
                h.setCellValue(col.hint() == null ? "" : col.hint());
                h.setCellStyle(hintStyle);
                sheet.setColumnWidth(i, Math.max(col.width(), col.headerText().length() * 2 + 2) * 256);
                sheet.setDefaultColumnStyle(i, col.type() == ExcelColumn.Type.DATE ? dateFormat : textFormat);
                if (!col.options().isEmpty()) {
                    DataValidation validation = dv.createValidation(
                            dv.createExplicitListConstraint(col.options().toArray(String[]::new)),
                            new CellRangeAddressList(2, MAX_IMPORT_ROWS + 1, i, i));
                    validation.setShowErrorBox(true);
                    sheet.addValidationData(validation);
                }
            }
            sheet.createFreezePane(0, 2);
            wb.write(response.getOutputStream());
        }
    }

    // ==================== 读取 ====================

    /** 读取导入文件：第一行列名、第二行说明（跳过），从第三行开始为数据；空行忽略 */
    public static List<ImportRow> read(MultipartFile file, List<? extends ExcelColumn<?>> columns) {
        try (InputStream in = file.getInputStream()) {
            return read(in, columns);
        } catch (IOException e) {
            throw BizException.of(GlobalErrorCodes.IMPORT_FILE_INVALID, "文件读取失败");
        }
    }

    public static List<ImportRow> read(InputStream in, List<? extends ExcelColumn<?>> columns) {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row head = sheet.getRow(0);
            if (head == null) throw BizException.of(GlobalErrorCodes.IMPORT_FILE_INVALID, "缺少列名行");
            DataFormatter formatter = new DataFormatter();
            Map<Integer, String> keyByIndex = new HashMap<>();
            Map<String, String> keyByLabel = new HashMap<>();
            columns.forEach(c -> keyByLabel.put(c.label(), c.key()));
            for (Cell c : head) {
                String label = formatter.formatCellValue(c).trim().replace("*", "");
                String key = keyByLabel.get(label);
                if (key != null) keyByIndex.put(c.getColumnIndex(), key);
            }
            List<String> missing = columns.stream().filter(ExcelColumn::required)
                    .filter(c -> !keyByIndex.containsValue(c.key())).map(ExcelColumn::label).toList();
            if (!missing.isEmpty()) {
                throw BizException.of(GlobalErrorCodes.IMPORT_FILE_INVALID, "缺少必填列：" + String.join("、", missing) + "，请使用下载的模板");
            }
            List<ImportRow> rows = new ArrayList<>();
            for (int r = 2; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> data = new LinkedHashMap<>();
                boolean empty = true;
                for (Map.Entry<Integer, String> e : keyByIndex.entrySet()) {
                    String v = cellText(row.getCell(e.getKey()), formatter);
                    data.put(e.getValue(), v);
                    if (!v.isEmpty()) empty = false;
                }
                if (empty) continue;
                if (rows.size() >= MAX_IMPORT_ROWS) throw BizException.of(GlobalErrorCodes.IMPORT_TOO_MANY_ROWS, MAX_IMPORT_ROWS);
                rows.add(new ImportRow(r + 1, data));
            }
            return rows;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of(GlobalErrorCodes.IMPORT_FILE_INVALID, "请上传 .xlsx 格式的文件");
        }
    }

    private static String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                LocalDateTime dt = cell.getLocalDateTimeCellValue();
                return dt.toLocalTime().toSecondOfDay() == 0 ? dt.toLocalDate().format(DATE) : dt.format(DATE_TIME);
            }
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell).trim();
    }

    // ==================== 错误报告 ====================

    /** 原文件末尾增加“错误原因”列，错误行标红 */
    public static byte[] errorReport(MultipartFile file, List<ImportRow> rows) {
        Map<Integer, String> errors = new HashMap<>();
        rows.stream().filter(ImportRow::hasError).forEach(r -> errors.put(r.rowNo(), String.join("；", r.errors())));
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.getSheetAt(0);
            Row head = sheet.getRow(0);
            int col = Math.max(head.getLastCellNum(), 0);
            Cell h = head.createCell(col);
            h.setCellValue("错误原因");
            h.setCellStyle(headerStyle(wb));
            sheet.setColumnWidth(col, 60 * 256);
            CellStyle red = wb.createCellStyle();
            red.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            red.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = wb.createFont();
            font.setColor(IndexedColors.RED.getIndex());
            CellStyle msgStyle = wb.createCellStyle();
            msgStyle.setFont(font);
            for (Map.Entry<Integer, String> e : errors.entrySet()) {
                Row row = sheet.getRow(e.getKey() - 1);
                if (row == null) continue;
                for (int i = 0; i < col; i++) {
                    Cell c = row.getCell(i);
                    if (c == null) c = row.createCell(i);
                    c.setCellStyle(red);
                }
                Cell m = row.createCell(col);
                m.setCellValue(e.getValue());
                m.setCellStyle(msgStyle);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw BizException.of(GlobalErrorCodes.IMPORT_FILE_INVALID, "文件读取失败");
        }
    }

    public static void writeBytes(HttpServletResponse response, String fileName, byte[] bytes) throws IOException {
        prepare(response, fileName);
        try (OutputStream out = response.getOutputStream()) {
            out.write(bytes);
        }
    }

    // ==================== 工具 ====================

    /** 导入时校验下拉值 */
    public static boolean inOptions(String value, Set<String> options) {
        return value == null || options.contains(value);
    }

    private static void prepare(HttpServletResponse response, String fileName) {
        response.setContentType(XLSX);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private static String safeSheetName(String name) {
        String s = name.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }
}
