package com.erp.module.engineering.service;

import com.erp.common.exception.BizException;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.engineering.api.bom.IssueMethod;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.controller.vo.BomVOs.BomSave;
import com.erp.module.engineering.controller.vo.BomVOs.ExplodeRow;
import com.erp.module.engineering.controller.vo.BomVOs.LineSave;
import com.erp.module.engineering.dal.dataobject.BomDO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.mapper.MaterialMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** BOM 导入导出（需求 05-03 4.1 导出、4.5 导入） */
@Service
public class BomExcelService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Set<MaterialType> PARENT_TYPES = Set.of(MaterialType.SEMI_FINISHED, MaterialType.FINISHED, MaterialType.PHANTOM);

    public static final List<ExcelColumn<Object>> IMPORT_COLUMNS = List.of(
            ExcelColumn.input("parentCode", "父件编码", true, "同一父件编码的连续行组成一个 BOM"),
            ExcelColumn.input("baseQty", "基数", false, "默认 1，取该父件第一行"),
            ExcelColumn.input("description", "版本说明", false, "取该父件第一行"),
            ExcelColumn.input("componentCode", "子件编码", true, null),
            ExcelColumn.input("qtyPer", "用量", true, "> 0"),
            ExcelColumn.input("scrapRate", "损耗率(%)", false, "0～100"),
            ExcelColumn.input("positionNo", "位号", false, "如 R1,R2,R5-R8"),
            ExcelColumn.<Object>input("issueMethod", "发料方式", false, "默认领料").options(List.of("领料", "倒冲")),
            ExcelColumn.<Object>input("isKey", "关键件", false, "是/否").options(List.of("是", "否")),
            ExcelColumn.input("remark", "备注", false, null));

    private final MaterialMapper materialMapper;
    private final BomService bomService;
    private final BomQueryService queryService;
    private final MaterialService materialService;
    private final TransactionTemplate tx;

    public BomExcelService(MaterialMapper materialMapper, BomService bomService, BomQueryService queryService, MaterialService materialService,
                           PlatformTransactionManager transactionManager) {
        this.materialMapper = materialMapper;
        this.bomService = bomService;
        this.queryService = queryService;
        this.materialService = materialService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ==================== 导出 ====================

    public static List<ExcelColumn<Map<String, Object>>> singleColumns() {
        return List.of(col("docNo", "BOM"), col("parentCode", "父件编码"), col("parentName", "父件名称"), col("version", "版本"),
                num("baseQty", "基数"), col("status", "状态"), num("lineNo", "行号"), col("code", "子件编码"), col("name", "子件名称"),
                col("spec", "规格"), col("uom", "单位"), num("qtyPer", "用量"), num("scrapRate", "损耗率(%)"), col("positionNo", "位号"),
                col("issueMethod", "发料方式"), col("isKey", "关键件"), col("substitutes", "替代料"), col("remark", "备注"));
    }

    public static List<ExcelColumn<Map<String, Object>>> multiColumns() {
        return List.of(col("docNo", "BOM"), col("parentCode", "顶层父件"), col("path", "层级"), num("level", "层"), col("code", "子件编码"),
                col("name", "子件名称"), col("spec", "规格"), col("uom", "单位"), num("qtyPer", "单层用量"), num("scrapRate", "损耗率(%)"),
                num("totalQtyPer", "累计用量"), col("sourceType", "取得方式"), col("phantom", "虚拟件"), col("childBom", "子 BOM 版本"));
    }

    private static ExcelColumn<Map<String, Object>> col(String key, String label) {
        return ExcelColumn.text(key, label, m -> m.get(key));
    }

    private static ExcelColumn<Map<String, Object>> num(String key, String label) {
        return ExcelColumn.number(key, label, m -> m.get(key));
    }

    public List<Map<String, Object>> exportRows(List<BomDO> boms, boolean multi) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BomDO b : boms) {
            if (multi) {
                String parentCode = b.getDocNo().substring(0, b.getDocNo().lastIndexOf("-V"));
                for (ExplodeRow r : BomQueryService.flatten(queryService.explodeTree(b.getId(), BigDecimal.ONE, 0))) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("docNo", b.getDocNo());
                    m.put("parentCode", parentCode);
                    m.put("path", "  ".repeat(r.level() - 1) + r.path());
                    m.put("level", r.level());
                    m.put("code", r.code());
                    m.put("name", r.name());
                    m.put("spec", r.spec());
                    m.put("uom", r.uom());
                    m.put("qtyPer", r.qtyPer());
                    m.put("scrapRate", pctOut(r.scrapRate()));
                    m.put("totalQtyPer", r.totalQtyPer());
                    m.put("sourceType", r.sourceType() == null ? null : r.sourceType().label());
                    m.put("phantom", r.phantom() ? "是" : "否");
                    m.put("childBom", r.bomVersion() == null ? null : "V" + r.bomVersion());
                    rows.add(m);
                }
            } else {
                var d = queryService.detail(b.getId());
                for (var l : d.lines()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("docNo", d.docNo());
                    m.put("parentCode", d.materialCode());
                    m.put("parentName", d.materialName());
                    m.put("version", "V" + d.version());
                    m.put("baseQty", d.baseQty());
                    m.put("status", "CLOSED".equals(d.status()) ? "停用" : com.erp.common.enums.DocStatus.valueOf(d.status()).label());
                    m.put("lineNo", l.lineNo());
                    m.put("code", l.componentCode());
                    m.put("name", l.componentName());
                    m.put("spec", l.componentSpec());
                    m.put("uom", l.uom());
                    m.put("qtyPer", l.qtyPer());
                    m.put("scrapRate", pctOut(l.scrapRate()));
                    m.put("positionNo", l.positionNo());
                    m.put("issueMethod", l.issueMethod() == null ? null : l.issueMethod().label());
                    m.put("isKey", l.isKey() ? "是" : "否");
                    m.put("substitutes", String.join("；", l.substitutes().stream().map(s -> s.code() + "×" + s.ratio().stripTrailingZeros().toPlainString()).toList()));
                    m.put("remark", l.remark());
                    rows.add(m);
                }
            }
        }
        return rows;
    }

    // ==================== 导入 ====================

    private record Group(MaterialDO parent, BomSave save, List<ImportRow> rows) {
    }

    /** 校验：同一父件的连续行组成一个 BOM；返回每行的动作说明 */
    public Map<Integer, String> check(List<ImportRow> rows) {
        Map<Integer, String> actions = new HashMap<>();
        for (Group g : parse(rows)) {
            String action = g.parent() == null ? null : "新建 " + g.parent().getCode() + " V" + queryService.nextVersion(g.parent().getId());
            g.rows().forEach(r -> actions.put(r.rowNo(), action));
        }
        return actions;
    }

    private List<Group> parse(List<ImportRow> rows) {
        Set<String> codes = new HashSet<>();
        rows.forEach(r -> {
            if (r.get("parentCode") != null) codes.add(r.get("parentCode").trim().toUpperCase());
            if (r.get("componentCode") != null) codes.add(r.get("componentCode").trim().toUpperCase());
        });
        Map<String, MaterialDO> materials = new HashMap<>();
        materialMapper.selectByCodes(codes).forEach(m -> materials.put(m.getCode(), m));
        boolean allowDraft = materialService.allowDraftComponent();
        List<Group> groups = new ArrayList<>();
        String current = null;
        List<ImportRow> chunk = new ArrayList<>();
        for (ImportRow r : rows) {
            String p = r.get("parentCode") == null ? "" : r.get("parentCode").trim().toUpperCase();
            if (!p.equals(current) && !chunk.isEmpty()) {
                groups.add(group(current, chunk, materials, allowDraft));
                chunk = new ArrayList<>();
            }
            current = p;
            chunk.add(r);
        }
        if (!chunk.isEmpty()) groups.add(group(current, chunk, materials, allowDraft));
        return groups;
    }

    private Group group(String parentCode, List<ImportRow> rows, Map<String, MaterialDO> materials, boolean allowDraft) {
        MaterialDO parent = materials.get(parentCode);
        ImportRow first = rows.get(0);
        if (parent == null) {
            rows.forEach(r -> r.error("父件「" + r.get("parentCode") + "」不存在"));
            return new Group(null, null, rows);
        }
        if (!PARENT_TYPES.contains(parent.getMaterialType())) {
            rows.forEach(r -> r.error("原材料、包材、辅料不能作为 BOM 父件"));
            return new Group(null, null, rows);
        }
        if (parent.getStatus() == MaterialStatus.DISABLED) {
            rows.forEach(r -> r.error("父件「" + parent.getCode() + "」已停用"));
            return new Group(null, null, rows);
        }
        BigDecimal baseQty = BigDecimal.ONE;
        if (first.get("baseQty") != null) {
            baseQty = decimal(first, "baseQty", "基数");
            if (baseQty != null && baseQty.signum() <= 0) first.error("基数必须大于 0");
        }
        List<LineSave> lines = new ArrayList<>();
        Map<Long, Integer> seen = new HashMap<>();
        for (ImportRow r : rows) {
            MaterialDO c = materials.get(r.get("componentCode") == null ? "" : r.get("componentCode").trim().toUpperCase());
            if (c == null) r.error("子件「" + r.get("componentCode") + "」不存在");
            else if (c.getId().equals(parent.getId())) r.error("子件不能与父件相同");
            else if (c.getStatus() != MaterialStatus.ENABLED && !(allowDraft && c.getStatus() == MaterialStatus.DRAFT)) r.error("子件「" + c.getCode() + "」未启用");
            else {
                Integer dup = seen.putIfAbsent(c.getId(), r.rowNo());
                if (dup != null) r.error("子件「" + c.getCode() + "」与第 " + dup + " 行重复，请合并为一行");
            }
            BigDecimal qty = decimal(r, "qtyPer", "用量");
            if (qty != null && qty.signum() <= 0) r.error("用量必须大于 0");
            BigDecimal scrap = decimal(r, "scrapRate", "损耗率");
            if (scrap != null && scrap.compareTo(HUNDRED) > 0) r.error("损耗率为 0～100");
            String im = r.get("issueMethod");
            IssueMethod method = im == null || "领料".equals(im) ? IssueMethod.PICK : "倒冲".equals(im) ? IssueMethod.BACKFLUSH : null;
            if (method == null) r.error("发料方式请填写“领料”或“倒冲”");
            String key = r.get("isKey");
            if (key != null && !"是".equals(key) && !"否".equals(key)) r.error("关键件请填写“是”或“否”");
            if (!r.hasError()) {
                lines.add(new LineSave(c.getId(), qty, scrap == null ? BigDecimal.ZERO : scrap.divide(HUNDRED, 6, RoundingMode.HALF_UP),
                        r.get("positionNo"), method, null, "是".equals(key), r.get("remark"), List.of()));
            }
        }
        if (rows.stream().anyMatch(ImportRow::hasError)) return new Group(parent, null, rows);
        List<Long> cycle = queryService.findCycle(parent.getId(), lines.stream().map(LineSave::componentId).toList(), null);
        if (!cycle.isEmpty()) {
            Map<Long, MaterialDO> ms = materialService.byIds(cycle);
            first.error("存在循环引用：" + String.join(" → ", cycle.stream().map(id -> ms.containsKey(id) ? ms.get(id).getCode() : String.valueOf(id)).toList()));
            return new Group(parent, null, rows);
        }
        return new Group(parent, new BomSave(parent.getId(), baseQty, first.get("description"), null, lines, List.of(), null), rows);
    }

    /** 执行导入：每个 BOM 独立事务；submit 为 true 时导入后提交审核 */
    public ImportResult doImport(List<ImportRow> rows, boolean submit) {
        int ok = 0;
        List<ImportResult.Error> errors = new ArrayList<>();
        for (Group g : parse(rows)) {
            if (g.save() == null) continue;
            try {
                tx.executeWithoutResult(s -> {
                    Long id = bomService.create(g.save()).id();
                    if (submit) bomService.submit(id);
                });
                ok++;
            } catch (BizException ex) {
                errors.add(new ImportResult.Error(g.rows().get(0).rowNo(), ex.getMessage()));
            }
        }
        return new ImportResult(ok, errors.size(), errors);
    }

    private static BigDecimal decimal(ImportRow r, String key, String name) {
        String v = r.get(key);
        if (v == null) return null;
        try {
            BigDecimal d = new BigDecimal(v.replace(",", ""));
            if (d.signum() < 0) r.error(name + "不能小于 0");
            return d;
        } catch (NumberFormatException e) {
            r.error(name + "必须是数字");
            return null;
        }
    }

    private static BigDecimal pctOut(BigDecimal v) {
        return v == null ? null : v.multiply(HUNDRED).stripTrailingZeros();
    }
}
