package com.erp.module.engineering.service;

import com.erp.common.exception.BizException;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.Tracking;
import com.erp.module.engineering.controller.vo.CategoryVOs.CategorySave;
import com.erp.module.engineering.dal.dataobject.ImportBatchDO;
import com.erp.module.engineering.dal.dataobject.MaterialCategoryDO;
import com.erp.module.engineering.dal.mapper.MaterialCategoryMapper;
import com.erp.module.system.api.uom.UomApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 物料类别导入（需求 05-01 第 9 节）：按文件顺序新建（上级须在系统中已存在或在文件中排在前面）；编码已存在的行跳过。
 * 每次导入记录为一个批次，可在「导入记录」中整批回滚。
 */
@Service
public class CategoryImportService {

    public static final List<ExcelColumn<Object>> IMPORT_COLUMNS = List.of(
            ExcelColumn.input("code", "类别编码", true, "1～16 位大写字母、数字，唯一"),
            ExcelColumn.input("name", "名称", true, "同一上级下唯一"),
            ExcelColumn.input("parentCode", "上级类别编码", false, "空为一级类别；上级可以在本文件中排在前面"),
            ExcelColumn.input("codePrefix", "编码前缀", true, "物料编码前缀，如 76-"),
            ExcelColumn.input("codeSeqLength", "流水号位数", false, "1～8，空则按编码规则（5 位）"),
            ExcelColumn.<Object>input("defaultMaterialType", "默认物料类型", true, null)
                    .options(Arrays.stream(MaterialType.values()).map(MaterialType::label).toList()),
            ExcelColumn.input("defaultBaseUom", "默认基本单位", false, "单位编码，如 PCS"),
            ExcelColumn.<Object>input("defaultTracking", "默认库存管理", false, "默认不管理")
                    .options(Arrays.stream(Tracking.values()).map(Tracking::label).toList()),
            ExcelColumn.<Object>input("defaultIqcRequired", "默认来料检验", false, "是 / 否，默认是").options(List.of("是", "否")),
            ExcelColumn.input("sort", "排序", false, "默认按文件顺序"),
            ExcelColumn.input("remark", "备注", false, null));

    private final MaterialCategoryService categoryService;
    private final MaterialCategoryMapper categoryMapper;
    private final UomApi uomApi;
    private final ImportBatchService batchService;
    private final TransactionTemplate tx;

    public CategoryImportService(MaterialCategoryService categoryService, MaterialCategoryMapper categoryMapper, UomApi uomApi,
                                 ImportBatchService batchService, PlatformTransactionManager transactionManager) {
        this.categoryService = categoryService;
        this.categoryMapper = categoryMapper;
        this.uomApi = uomApi;
        this.batchService = batchService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    record Parsed(CategorySave save, String parentCode) {
    }

    /** 校验；返回 行号 → 新增 / 跳过 */
    public Map<Integer, String> check(List<ImportRow> rows) {
        Map<Integer, String> actions = new HashMap<>();
        parse(rows, actions);
        return actions;
    }

    private Map<Integer, Parsed> parse(List<ImportRow> rows, Map<Integer, String> actions) {
        Map<String, MaterialCategoryDO> existing = new HashMap<>();
        categoryMapper.selectList(null).forEach(c -> existing.put(c.getCode(), c));
        Map<String, Integer> inFile = new HashMap<>();
        Map<Integer, Parsed> result = new LinkedHashMap<>();
        int sort = 10;
        for (ImportRow r : rows) {
            for (ExcelColumn<Object> c : IMPORT_COLUMNS) if (c.required() && r.get(c.key()) == null) r.error("请填写" + c.label());
            String code = upper(r.get("code"));
            if (code != null && !code.matches("[A-Z0-9]{1,16}")) r.error("类别编码为 1～16 位字母、数字");
            if (code != null && inFile.putIfAbsent(code, r.rowNo()) != null) r.error("类别编码与第 " + inFile.get(code) + " 行重复");
            if (code != null && existing.containsKey(code)) {
                actions.put(r.rowNo(), "跳过（已存在）");
                continue;
            }
            String parent = upper(r.get("parentCode"));
            if (parent != null && !existing.containsKey(parent) && (!inFile.containsKey(parent) || inFile.get(parent) >= r.rowNo())) {
                r.error("上级类别编码「" + parent + "」不存在（须已存在，或在本文件中排在前面）");
            }
            String prefix = upper(r.get("codePrefix"));
            if (prefix != null && !prefix.matches("[A-Z0-9][A-Z0-9-]{0,7}")) r.error("编码前缀为 1～8 位字母、数字或 -，不能以 - 开头");
            Integer seq = null;
            if (r.get("codeSeqLength") != null) {
                try {
                    seq = Integer.valueOf(r.get("codeSeqLength").trim());
                    if (seq < 1 || seq > 8) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    r.error("流水号位数为 1～8 的整数");
                }
            }
            MaterialType type = byLabel(r, "defaultMaterialType", MaterialType.values(), MaterialType::label, "默认物料类型");
            Tracking tracking = byLabel(r, "defaultTracking", Tracking.values(), Tracking::label, "默认库存管理");
            String uom = upper(r.get("defaultBaseUom"));
            if (uom != null) {
                try {
                    uom = uomApi.validate(uom).code();
                } catch (BizException e) {
                    r.error(e.getMessage());
                }
            }
            String iqc = r.get("defaultIqcRequired");
            if (iqc != null && !iqc.equals("是") && !iqc.equals("否")) r.error("默认来料检验请填写 是 / 否");
            Integer sortValue = sort;
            if (r.get("sort") != null) {
                try {
                    sortValue = Integer.valueOf(r.get("sort").trim());
                } catch (NumberFormatException e) {
                    r.error("排序必须是整数");
                }
            }
            sort += 10;
            if (r.get("name") != null && r.get("name").length() > 64) r.error("名称最多 64 个字");
            if (r.hasError()) continue;
            actions.put(r.rowNo(), "新增");
            result.put(r.rowNo(), new Parsed(new CategorySave(null, code, r.get("name").trim(), prefix, seq, type, uom,
                    tracking == null ? Tracking.NONE : tracking, !"否".equals(iqc), null, sortValue, r.get("remark"), null), parent));
        }
        return result;
    }

    public ImportResult doImport(List<ImportRow> rows, String fileName) {
        Map<Integer, Parsed> parsed = parse(rows, new HashMap<>());
        ImportBatchDO batch = batchService.begin(ImportBatchService.CATEGORY, fileName, null, rows.size());
        int ok = 0;
        List<ImportResult.Error> errors = new ArrayList<>();
        for (Map.Entry<Integer, Parsed> e : parsed.entrySet()) {
            try {
                tx.executeWithoutResult(s -> {
                    Parsed p = e.getValue();
                    Long parentId = p.parentCode() == null ? null : categoryMapper.selectByCode(p.parentCode()).getId();
                    CategorySave v = p.save();
                    Long id = categoryService.create(new CategorySave(parentId, v.code(), v.name(), v.codePrefix(), v.codeSeqLength(),
                            v.defaultMaterialType(), v.defaultBaseUom(), v.defaultTracking(), v.defaultIqcRequired(), v.defaultShelfLifeDays(),
                            v.sort(), v.remark(), null));
                    batchService.created(batch, id, v.code(), e.getKey());
                });
                ok++;
            } catch (BizException ex) {
                errors.add(new ImportResult.Error(e.getKey(), ex.getMessage()));
            } catch (NullPointerException ex) {
                errors.add(new ImportResult.Error(e.getKey(), "上级类别不存在（上级所在行导入失败）"));
            }
        }
        batchService.finish(batch);
        return new ImportResult(ok, errors.size(), errors);
    }

    private static String upper(String v) {
        return v == null || v.isBlank() ? null : v.trim().toUpperCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E byLabel(ImportRow r, String key, E[] values, java.util.function.Function<E, String> label, String name) {
        String v = r.get(key);
        if (v == null) return null;
        for (E e : values) if (label.apply(e).equals(v.trim()) || e.name().equalsIgnoreCase(v.trim())) return e;
        r.error(name + "「" + v + "」不正确");
        return null;
    }
}
