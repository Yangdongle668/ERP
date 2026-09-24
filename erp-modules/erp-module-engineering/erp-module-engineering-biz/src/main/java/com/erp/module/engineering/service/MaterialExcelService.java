package com.erp.module.engineering.service;

import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.engineering.api.material.IssueRule;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.OrderPolicy;
import com.erp.module.engineering.api.material.SourceType;
import com.erp.module.engineering.api.material.Tracking;
import com.erp.module.engineering.controller.vo.MaterialRespVO;
import com.erp.module.engineering.controller.vo.MaterialSaveReqVO;
import com.erp.module.engineering.dal.dataobject.MaterialCategoryDO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.mapper.MaterialCategoryMapper;
import com.erp.module.engineering.dal.mapper.MaterialMapper;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.uom.UomApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 物料导入导出（需求 05-02 3.5 与导出按钮） */
@Service
public class MaterialExcelService {

    public static final String MODE_SKIP = "SKIP";
    public static final String MODE_UPDATE = "UPDATE";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static final List<ExcelColumn<Object>> IMPORT_COLUMNS = List.of(
            ExcelColumn.input("categoryCode", "物料类别编码", true, "末级类别编码，如 FPC"),
            ExcelColumn.input("code", "编码", false, "留空自动生成"),
            ExcelColumn.input("name", "名称", true, null),
            ExcelColumn.input("nameEn", "英文名称", false, null),
            ExcelColumn.input("spec", "规格型号", false, null),
            ExcelColumn.<Object>input("materialType", "物料类型", true, null).options(labels(MaterialType.values(), MaterialType::label)),
            ExcelColumn.input("baseUom", "基本单位", true, "单位编码，如 PCS"),
            ExcelColumn.input("drawingNo", "图号", false, null),
            ExcelColumn.input("revision", "版本", false, null),
            ExcelColumn.input("brand", "品牌", false, null),
            ExcelColumn.input("manufacturer", "制造商", false, null),
            ExcelColumn.input("mpn", "制造商料号", false, null),
            ExcelColumn.input("hsCode", "海关编码", false, "4～10 位数字"),
            ExcelColumn.input("unitNetWeight", "单位净重", false, "kg"),
            ExcelColumn.input("unitGrossWeight", "单位毛重", false, "kg"),
            ExcelColumn.<Object>input("sourceType", "取得方式", false, "留空按物料类型").options(labels(SourceType.values(), SourceType::label)),
            ExcelColumn.input("leadTimeDays", "提前期", false, "天"),
            ExcelColumn.input("safetyStock", "安全库存", false, null),
            ExcelColumn.input("moq", "MOQ", false, null),
            ExcelColumn.input("mpq", "MPQ", false, null),
            ExcelColumn.input("buyer", "采购员用户名", false, null),
            ExcelColumn.input("planner", "计划员用户名", false, null),
            ExcelColumn.<Object>input("tracking", "库存管理", false, "留空取类别默认").options(labels(Tracking.values(), Tracking::label)),
            ExcelColumn.input("shelfLifeDays", "保质期", false, "天"),
            ExcelColumn.<Object>input("iqcRequired", "来料检验", false, "是/否").options(List.of("是", "否")),
            ExcelColumn.<Object>input("fqcRequired", "完工检验", false, "是/否").options(List.of("是", "否")),
            ExcelColumn.<Object>input("oqcRequired", "出货检验", false, "是/否").options(List.of("是", "否")),
            ExcelColumn.input("standardCost", "标准成本", false, "需要成本字段权限，否则忽略"),
            ExcelColumn.input("purchaseTaxRate", "进项税率(%)", false, "如 13"),
            ExcelColumn.input("salesTaxRate", "销项税率(%)", false, "如 13"),
            ExcelColumn.input("remark", "备注", false, null));

    private final MaterialService materialService;
    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final UomApi uomApi;
    private final UserApi userApi;
    private final ParamApi paramApi;
    private final TransactionTemplate tx;

    public MaterialExcelService(MaterialService materialService, MaterialMapper materialMapper, MaterialCategoryMapper categoryMapper, UomApi uomApi, UserApi userApi, ParamApi paramApi,
                                PlatformTransactionManager transactionManager) {
        this.materialService = materialService;
        this.materialMapper = materialMapper;
        this.categoryMapper = categoryMapper;
        this.uomApi = uomApi;
        this.userApi = userApi;
        this.paramApi = paramApi;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ==================== 导出 ====================

    public static List<ExcelColumn<MaterialRespVO>> exportColumns() {
        return List.of(
                ExcelColumn.<MaterialRespVO>text("code", "编码", MaterialRespVO::code),
                ExcelColumn.<MaterialRespVO>text("name", "名称", MaterialRespVO::name).width(24),
                ExcelColumn.<MaterialRespVO>text("nameEn", "英文名称", MaterialRespVO::nameEn),
                ExcelColumn.<MaterialRespVO>text("spec", "规格型号", MaterialRespVO::spec).width(30),
                ExcelColumn.<MaterialRespVO>text("categoryName", "类别", MaterialRespVO::categoryName),
                ExcelColumn.<MaterialRespVO>text("materialType", "物料类型", m -> m.materialType() == null ? null : m.materialType().label()),
                ExcelColumn.<MaterialRespVO>text("baseUom", "基本单位", MaterialRespVO::baseUom),
                ExcelColumn.<MaterialRespVO>text("drawingNo", "图号", MaterialRespVO::drawingNo),
                ExcelColumn.<MaterialRespVO>text("revision", "版本", MaterialRespVO::revision),
                ExcelColumn.<MaterialRespVO>text("brand", "品牌", MaterialRespVO::brand),
                ExcelColumn.<MaterialRespVO>text("manufacturer", "制造商", MaterialRespVO::manufacturer),
                ExcelColumn.<MaterialRespVO>text("mpn", "制造商料号", MaterialRespVO::mpn),
                ExcelColumn.<MaterialRespVO>text("hsCode", "海关编码", MaterialRespVO::hsCode),
                ExcelColumn.<MaterialRespVO>number("unitNetWeight", "单位净重", MaterialRespVO::unitNetWeight),
                ExcelColumn.<MaterialRespVO>number("unitGrossWeight", "单位毛重", MaterialRespVO::unitGrossWeight),
                ExcelColumn.<MaterialRespVO>text("sourceType", "取得方式", m -> m.sourceType() == null ? null : m.sourceType().label()),
                ExcelColumn.<MaterialRespVO>number("leadTimeDays", "提前期", MaterialRespVO::leadTimeDays),
                ExcelColumn.<MaterialRespVO>number("safetyStock", "安全库存", MaterialRespVO::safetyStock),
                ExcelColumn.<MaterialRespVO>number("maxStock", "最高库存", MaterialRespVO::maxStock),
                ExcelColumn.<MaterialRespVO>text("orderPolicy", "批量规则", m -> m.orderPolicy() == null ? null : m.orderPolicy().label()),
                ExcelColumn.<MaterialRespVO>number("moq", "MOQ", MaterialRespVO::moq),
                ExcelColumn.<MaterialRespVO>number("mpq", "MPQ", MaterialRespVO::mpq),
                ExcelColumn.<MaterialRespVO>text("plannerName", "计划员", MaterialRespVO::plannerName),
                ExcelColumn.<MaterialRespVO>text("buyerName", "采购员", MaterialRespVO::buyerName),
                ExcelColumn.<MaterialRespVO>text("purchaseUom", "采购单位", MaterialRespVO::purchaseUom),
                ExcelColumn.<MaterialRespVO>number("overReceivePct", "允许超收(%)", m -> pctOut(m.overReceivePct())),
                ExcelColumn.<MaterialRespVO>text("tracking", "库存管理", m -> m.tracking() == null ? null : m.tracking().label()),
                ExcelColumn.<MaterialRespVO>text("issueRule", "出库规则", m -> m.issueRule() == null ? null : m.issueRule().label()),
                ExcelColumn.<MaterialRespVO>number("shelfLifeDays", "保质期(天)", MaterialRespVO::shelfLifeDays),
                ExcelColumn.<MaterialRespVO>text("iqcRequired", "来料检验", m -> yesNo(m.iqcRequired())),
                ExcelColumn.<MaterialRespVO>text("fqcRequired", "完工检验", m -> yesNo(m.fqcRequired())),
                ExcelColumn.<MaterialRespVO>text("oqcRequired", "出货检验", m -> yesNo(m.oqcRequired())),
                ExcelColumn.<MaterialRespVO>number("standardCost", "标准成本", MaterialRespVO::standardCost),
                ExcelColumn.<MaterialRespVO>text("salesUom", "销售单位", MaterialRespVO::salesUom),
                ExcelColumn.<MaterialRespVO>number("purchaseTaxRate", "进项税率(%)", m -> pctOut(m.purchaseTaxRate())),
                ExcelColumn.<MaterialRespVO>number("salesTaxRate", "销项税率(%)", m -> pctOut(m.salesTaxRate())),
                ExcelColumn.<MaterialRespVO>text("status", "状态", m -> m.status() == null ? null : m.status().label()),
                ExcelColumn.<MaterialRespVO>text("remark", "备注", MaterialRespVO::remark),
                ExcelColumn.<MaterialRespVO>dateTime("createdAt", "创建时间", MaterialRespVO::createdAt),
                ExcelColumn.<MaterialRespVO>dateTime("updatedAt", "更新时间", MaterialRespVO::updatedAt));
    }

    // ==================== 导入校验 ====================

    /** 解析后的行：保存请求与已存在的物料（更新时） */
    private record Parsed(MaterialSaveReqVO req, MaterialDO existing) {
    }

    /**
     * 校验：逐行写入错误；返回每行的动作（新增 / 更新 / 跳过）。
     *
     * @param mode SKIP 编码已存在的行跳过；UPDATE 更新文件中非空的列
     */
    public Map<Integer, String> check(List<ImportRow> rows, String mode) {
        Map<Integer, String> actions = new HashMap<>();
        parse(rows, mode, actions);
        return actions;
    }

    private Map<Integer, Parsed> parse(List<ImportRow> rows, String mode, Map<Integer, String> actions) {
        Map<String, MaterialCategoryDO> categories = new HashMap<>();
        categoryMapper.selectList(null).forEach(c -> categories.put(c.getCode(), c));
        Set<Long> parents = new HashSet<>();
        categories.values().forEach(c -> {
            if (c.getParentId() != null) parents.add(c.getParentId());
        });
        Set<String> codes = new HashSet<>();
        rows.forEach(r -> {
            if (r.get("code") != null) codes.add(r.get("code").trim().toUpperCase());
        });
        Map<String, MaterialDO> existing = new HashMap<>();
        materialMapper.selectByCodes(codes).forEach(m -> existing.put(m.getCode(), m));
        Map<String, Integer> seen = new HashMap<>();
        Map<String, Long> userIds = new HashMap<>();
        boolean costAllowed = MaterialService.canViewCost();
        boolean block = "BLOCK".equals(paramApi.getString(MaterialService.PARAM_DUP));
        Map<Integer, Parsed> result = new LinkedHashMap<>();
        for (ImportRow r : rows) {
            String code = r.get("code") == null ? null : r.get("code").trim().toUpperCase();
            if (code != null) {
                Integer first = seen.putIfAbsent(code, r.rowNo());
                if (first != null) r.error("编码与第 " + first + " 行重复");
                if (!code.matches("^[A-Z0-9._-]{1,64}$")) r.error("编码只能包含字母、数字和 . _ -，最多 64 位");
            }
            MaterialDO old = code == null ? null : existing.get(code);
            if (old != null && MODE_SKIP.equals(mode)) {
                actions.put(r.rowNo(), "跳过");
                continue;
            }
            MaterialCategoryDO cat = categories.get(upper(r.get("categoryCode")));
            if (cat == null) r.error("物料类别编码「" + r.get("categoryCode") + "」不存在");
            else if (cat.getStatus() != EnableStatus.ENABLED || parents.contains(cat.getId())) r.error("请选择末级物料类别");
            MaterialType type = parseEnum(r, "materialType", MaterialType.values(), MaterialType::label, "物料类型");
            String uom = upper(r.get("baseUom"));
            if (uom != null) {
                try {
                    uom = uomApi.validate(uom).code();
                } catch (BizException e) {
                    r.error(e.getMessage());
                }
            }
            if (r.get("name") != null && r.get("name").length() > 128) r.error("名称不能超过 128 个字");
            String hs = r.get("hsCode");
            if (hs != null && !hs.matches("^\\d{4,10}$")) r.error("海关编码为 4～10 位数字");
            BigDecimal net = num(r, "unitNetWeight", "单位净重");
            BigDecimal gross = num(r, "unitGrossWeight", "单位毛重");
            SourceType source = parseEnum(r, "sourceType", SourceType.values(), SourceType::label, "取得方式");
            Integer lead = integer(r, "leadTimeDays", "提前期", 0, 365);
            BigDecimal safety = num(r, "safetyStock", "安全库存");
            BigDecimal moq = num(r, "moq", "MOQ");
            BigDecimal mpq = num(r, "mpq", "MPQ");
            Long buyer = user(r, "buyer", "采购员", userIds);
            Long planner = user(r, "planner", "计划员", userIds);
            Tracking tracking = parseEnum(r, "tracking", Tracking.values(), Tracking::label, "库存管理");
            Integer shelf = integer(r, "shelfLifeDays", "保质期", 1, 3650);
            Boolean iqc = bool(r, "iqcRequired", "来料检验");
            Boolean fqc = bool(r, "fqcRequired", "完工检验");
            Boolean oqc = bool(r, "oqcRequired", "出货检验");
            BigDecimal cost = costAllowed ? num(r, "standardCost", "标准成本") : null;
            BigDecimal pTax = pct(r, "purchaseTaxRate", "进项税率");
            BigDecimal sTax = pct(r, "salesTaxRate", "销项税率");
            if (r.hasError()) continue;
            if (old != null && old.getStatus() != MaterialStatus.DRAFT && (type != old.getMaterialType() || !uom.equals(old.getBaseUom()))) {
                r.error("物料「" + old.getCode() + "」已启用，物料类型、基本单位不能修改");
                continue;
            }
            MaterialRespVO base = old == null ? null : materialService.get(old.getId());
            MaterialSaveReqVO req = new MaterialSaveReqVO(code, r.get("name"), or(r.get("nameEn"), base, MaterialRespVO::nameEn),
                    or(r.get("spec"), base, MaterialRespVO::spec), type, cat.getId(), uom,
                    or(r.get("drawingNo"), base, MaterialRespVO::drawingNo), or(r.get("revision"), base, MaterialRespVO::revision),
                    or(r.get("brand"), base, MaterialRespVO::brand), or(r.get("manufacturer"), base, MaterialRespVO::manufacturer),
                    or(r.get("mpn"), base, MaterialRespVO::mpn), or(hs, base, MaterialRespVO::hsCode),
                    or(net, base, MaterialRespVO::unitNetWeight), or(gross, base, MaterialRespVO::unitGrossWeight),
                    base == null ? null : base.imageFileId(), or(r.get("remark"), base, MaterialRespVO::remark),
                    or(source, base, MaterialRespVO::sourceType), or(lead, base, MaterialRespVO::leadTimeDays), or(safety, base, MaterialRespVO::safetyStock),
                    base == null ? null : base.maxStock(), base == null ? null : base.orderPolicy(), base == null ? null : base.fixedLotQty(),
                    base == null ? null : base.periodDays(), or(moq, base, MaterialRespVO::moq), or(mpq, base, MaterialRespVO::mpq),
                    or(planner, base, MaterialRespVO::plannerId), or(buyer, base, MaterialRespVO::buyerId),
                    base == null ? null : base.purchaseUom(), base == null ? null : base.overReceivePct(),
                    or(tracking, base, MaterialRespVO::tracking), base == null ? null : base.issueRule(), or(shelf, base, MaterialRespVO::shelfLifeDays),
                    base == null ? null : base.minRemainingLifePct(),
                    or(iqc, base, MaterialRespVO::iqcRequired), or(fqc, base, MaterialRespVO::fqcRequired), or(oqc, base, MaterialRespVO::oqcRequired),
                    or(cost, base, MaterialRespVO::standardCost), base == null ? null : base.salesUom(),
                    or(pTax, base, MaterialRespVO::purchaseTaxRate), or(sTax, base, MaterialRespVO::salesTaxRate),
                    base == null ? null : base.uoms().stream().map(u -> new MaterialSaveReqVO.UomSave(u.uom(), u.rate(), u.remark())).toList(),
                    base == null ? null : base.version());
            if (block) {
                var dups = materialService.suspects(cat.getId(), req.name(), req.spec(), req.mpn(), old == null ? null : old.getId());
                if (!dups.isEmpty()) {
                    r.error("发现疑似重复物料：" + String.join("、", dups.stream().map(d -> d.code()).toList()));
                    continue;
                }
            }
            actions.put(r.rowNo(), old == null ? "新增" : "更新");
            result.put(r.rowNo(), new Parsed(req, old));
        }
        return result;
    }

    /** 执行导入：每行独立事务；enable 为 true 时导入后直接启用 */
    public ImportResult doImport(List<ImportRow> rows, String mode, boolean enable) {
        Map<Integer, Parsed> parsed = parse(rows, mode, new HashMap<>());
        int ok = 0;
        List<ImportResult.Error> errors = new ArrayList<>();
        for (Map.Entry<Integer, Parsed> e : parsed.entrySet()) {
            try {
                tx.executeWithoutResult(s -> {
                    Parsed p = e.getValue();
                    Long id;
                    if (p.existing() == null) id = materialService.create(p.req());
                    else {
                        id = p.existing().getId();
                        materialService.update(id, p.req());
                    }
                    MaterialDO m = materialMapper.selectById(id);
                    if (enable && (m.getStatus() == MaterialStatus.DRAFT || m.getStatus() == MaterialStatus.DISABLED)) materialService.enable(id);
                });
                ok++;
            } catch (BizException ex) {
                errors.add(new ImportResult.Error(e.getKey(), ex.getMessage()));
            }
        }
        return new ImportResult(ok, errors.size(), errors);
    }

    // ==================== 工具 ====================

    private static <E extends Enum<E>> List<String> labels(E[] values, Function<E, String> label) {
        return Arrays.stream(values).map(label).toList();
    }

    private static <E extends Enum<E>> E parseEnum(ImportRow r, String key, E[] values, Function<E, String> label, String name) {
        String v = r.get(key);
        if (v == null) return null;
        for (E e : values) if (label.apply(e).equals(v) || e.name().equalsIgnoreCase(v)) return e;
        r.error(name + "「" + v + "」不正确");
        return null;
    }

    private static BigDecimal num(ImportRow r, String key, String name) {
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

    private static Integer integer(ImportRow r, String key, String name, int min, int max) {
        BigDecimal d = num(r, key, name);
        if (d == null) return null;
        if (d.scale() > 0 && d.stripTrailingZeros().scale() > 0 || d.intValue() < min || d.intValue() > max) {
            r.error(name + "为 " + min + "～" + max + " 的整数");
            return null;
        }
        return d.intValue();
    }

    private static BigDecimal pct(ImportRow r, String key, String name) {
        BigDecimal d = num(r, key, name);
        if (d == null) return null;
        if (d.compareTo(HUNDRED) > 0) {
            r.error(name + "为 0～100");
            return null;
        }
        return d.divide(HUNDRED, 6, RoundingMode.HALF_UP);
    }

    private static Boolean bool(ImportRow r, String key, String name) {
        String v = r.get(key);
        if (v == null) return null;
        if ("是".equals(v)) return true;
        if ("否".equals(v)) return false;
        r.error(name + "请填写“是”或“否”");
        return null;
    }

    private Long user(ImportRow r, String key, String name, Map<String, Long> cache) {
        String v = r.get(key);
        if (v == null) return null;
        Long id = cache.computeIfAbsent(v.toLowerCase(), k -> userApi.getByUsername(k).map(UserDTO::id).orElse(-1L));
        if (id < 0) {
            r.error(name + "「" + v + "」不存在");
            return null;
        }
        return id;
    }

    private static <T> T or(T value, MaterialRespVO base, Function<MaterialRespVO, T> getter) {
        return value != null ? value : base == null ? null : getter.apply(base);
    }

    private static String upper(String s) {
        return s == null ? null : s.trim().toUpperCase();
    }

    private static BigDecimal pctOut(BigDecimal v) {
        return v == null ? null : v.multiply(HUNDRED).stripTrailingZeros();
    }

    private static String yesNo(Boolean b) {
        return b == null ? null : b ? "是" : "否";
    }

    /** 导入前的权限与参数检查：导入后直接启用需要启用权限，且参数不要求审批 */
    public boolean enableAllowed() {
        return !paramApi.getBool(MaterialService.PARAM_APPROVAL);
    }
}
