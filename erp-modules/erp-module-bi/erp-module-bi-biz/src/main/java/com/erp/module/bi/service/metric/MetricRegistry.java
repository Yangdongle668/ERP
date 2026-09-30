package com.erp.module.bi.service.metric;

import com.erp.module.bi.config.BiModuleConfig;
import com.erp.module.bi.service.metric.MetricDefinition.Mode;
import com.erp.module.bi.service.metric.MetricDefinition.Source;
import com.erp.module.bi.service.metric.MetricDefinition.Unit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import static com.erp.module.bi.service.metric.MetricDefinition.dims;

/**
 * 内置指标（需求 13-01 第 2 节）。金额均为本位币；比率类指标为百分数（保留 2 位）。
 * <p>SQL 片段只由本类常量组成（列名、聚合函数），查询引擎不会拼接用户输入。
 */
public final class MetricRegistry {

    /** 维度编码 → 名称 */
    public static final Map<String, String> DIMENSIONS = new LinkedHashMap<>();

    static {
        DIMENSIONS.put("date", "日期");
        DIMENSIONS.put("customer", "客户");
        DIMENSIONS.put("country", "国家/地区");
        DIMENSIONS.put("owner", "负责人");
        DIMENSIONS.put("dept", "部门");
        DIMENSIONS.put("supplier", "供应商");
        DIMENSIONS.put("category", "物料类别");
        DIMENSIONS.put("material", "物料");
        DIMENSIONS.put("warehouse_type", "仓库类型");
        DIMENSIONS.put("warehouse", "仓库");
        DIMENSIONS.put("inspect_type", "检验类型");
    }

    /** 呆滞天数占位符（查询时替换为参数值） */
    public static final String SLOW_DAYS = "{slowDays}";

    private static final Map<String, MetricDefinition> METRICS = new LinkedHashMap<>();

    private static final Map<String, String> SALES_DIMS = dims("date", "stat_date", "customer", "customer_id", "country", "country", "owner", "owner_id",
            "dept", "dept_id", "category", "category_id", "material", "material_id");
    private static final Map<String, String> PURCHASE_DIMS = dims("date", "stat_date", "supplier", "supplier_id", "owner", "owner_id", "dept", "dept_id",
            "category", "category_id", "material", "material_id");
    private static final Map<String, String> PRODUCTION_DIMS = dims("date", "stat_date", "dept", "dept_id", "category", "category_id", "material",
            "material_id");
    private static final Map<String, String> SNAPSHOT_DIMS = dims("date", "stat_date", "warehouse_type", "warehouse_type", "warehouse", "warehouse_id",
            "category", "category_id", "material", "material_id");
    private static final Map<String, String> MONTHLY_DIMS = dims("date", "period", "warehouse_type", "warehouse_type", "category", "category_id",
            "material", "material_id");
    private static final Map<String, String> AR_DIMS = dims("date", "period", "customer", "partner_id", "owner", "owner_id", "dept", "dept_id");
    private static final Map<String, String> AP_DIMS = dims("date", "period", "supplier", "partner_id", "owner", "owner_id", "dept", "dept_id");

    static {
        String sales = "sales";
        String fin = BiModuleConfig.PERM_FINANCE;
        String salesPerm = BiModuleConfig.PERM_SALES;
        // ---------- 销售 ----------
        add("sales_order_amount", "接单额", sales, Unit.AMOUNT, "期间内审核的销售订单价税合计（本位币，按订单汇率），变更按差额计入变更审核日，关闭不扣减",
                Source.SALES, "SUM(order_amount)", null, SALES_DIMS, salesPerm, false);
        add("sales_ship_amount", "出货额", sales, Unit.AMOUNT, "期间内出货确认金额（本位币，不含税），退货冲减",
                Source.SALES, "SUM(ship_amount - return_amount)", null, SALES_DIMS, salesPerm, false);
        add("sales_return_amount", "退货额", sales, Unit.AMOUNT, "期间内退货收货金额（本位币，不含税）",
                Source.SALES, "SUM(return_amount)", null, SALES_DIMS, salesPerm, false);
        add("sales_ship_qty", "出货数量", sales, Unit.QTY, "期间内出货数量（基本单位）",
                Source.SALES, "SUM(ship_qty)", null, SALES_DIMS, salesPerm, false);
        add("sales_receipt_amount", "回款额", sales, Unit.AMOUNT, "期间内收款核销到应收 + 预收确认金额（本位币）",
                Source.SALES, "SUM(receipt_amount)", null, pick(SALES_DIMS, "date", "customer", "country", "owner", "dept"), salesPerm, false);
        add("on_time_delivery_rate", "交期达成率", sales, Unit.PERCENT, "期间内完成首次出货的订单行中，首次出货日 ≤ 承诺交期（无则要求交期）的行数占比",
                Source.SALES, "SUM(on_time_line_count)", "SUM(ship_line_count)", SALES_DIMS, salesPerm, false);
        add("ship_customer_count", "出货客户数", sales, Unit.COUNT, "期间内有出货的客户数",
                Source.SALES, "COUNT(DISTINCT CASE WHEN ship_amount > 0 THEN customer_id END)", null,
                pick(SALES_DIMS, "date", "country", "owner", "dept", "category", "material"), salesPerm, false);
        special("new_customer_count", "新客户数", sales, Unit.COUNT, "首次审核销售订单日期在期间内的客户数", dims("date", "stat_date"), salesPerm);
        add("ship_cost", "出货成本", sales, Unit.AMOUNT, "出货数量 × 出货期间单位成本（成本计算完成后）",
                Source.SALES, "SUM(ship_cost)", null, SALES_DIMS, fin, true);
        add("gross_profit", "毛利额", sales, Unit.AMOUNT, "出货额 − 出货成本（只含已计算成本的出货）",
                Source.SALES, "SUM(costed_ship_amount - ship_cost)", null, SALES_DIMS, fin, true);
        add("gross_margin", "毛利率", sales, Unit.PERCENT, "毛利额 ÷ 已计算成本的出货额",
                Source.SALES, "SUM(costed_ship_amount - ship_cost)", "SUM(costed_ship_amount)", SALES_DIMS, fin, false);

        // ---------- 采购 ----------
        String pur = "purchase";
        String purPerm = BiModuleConfig.PERM_PURCHASE;
        add("purchase_amount", "采购额", pur, Unit.AMOUNT, "期间内审核的采购订单价税合计（本位币）",
                Source.PURCHASE, "SUM(order_amount)", null, PURCHASE_DIMS, purPerm, false);
        add("purchase_qty", "采购数量", pur, Unit.QTY, "期间内采购订单数量",
                Source.PURCHASE, "SUM(order_qty)", null, PURCHASE_DIMS, purPerm, false);
        add("purchase_avg_price", "采购均价", pur, Unit.PRICE, "采购额 ÷ 采购数量（按采购额加权，建议按物料查看）",
                Source.PURCHASE, "SUM(order_amount)", "SUM(order_qty)", PURCHASE_DIMS, purPerm, true, 1);
        add("receipt_amount", "到货额", pur, Unit.AMOUNT, "期间内合格入库金额（含特采，不含税本位币）",
                Source.PURCHASE, "SUM(receipt_amount)", null, PURCHASE_DIMS, purPerm, false);
        add("supplier_on_time_rate", "供应商准时率", pur, Unit.PERCENT, "期间内到期的采购订单行中，首次到货日 ≤ 确认交期（无则需求日期）的占比",
                Source.PURCHASE, "SUM(on_time_line_count)", "SUM(due_line_count)", pick(PURCHASE_DIMS, "date", "supplier", "owner", "dept"), purPerm, false);
        add("supplier_count", "供应商数", pur, Unit.COUNT, "期间内有采购订单的供应商数",
                Source.PURCHASE, "COUNT(DISTINCT CASE WHEN order_amount > 0 THEN supplier_id END)", null,
                pick(PURCHASE_DIMS, "date", "owner", "dept", "category", "material"), purPerm, false);

        // ---------- 品质 ----------
        String qc = "quality";
        String qcPerm = BiModuleConfig.PERM_QUALITY;
        Map<String, String> qualityDims = dims("date", "stat_date", "supplier", "supplier_id", "customer", "customer_id", "category", "category_id",
                "material", "material_id");
        add("iqc_lot_pass_rate", "来料批次合格率", qc, Unit.PERCENT, "期间内判定的来料检验批次中判定合格的占比（特采、拒收不计合格）",
                Source.QUALITY, typed("IQC", "pass_count"), typed("IQC", "lot_count"), pick(qualityDims, "date", "supplier", "category", "material"), qcPerm, false);
        add("fqc_pass_rate", "FQC 一次合格率", qc, Unit.PERCENT, "期间内判定的成品检验批次中判定合格的占比",
                Source.QUALITY, typed("FQC", "pass_count"), typed("FQC", "lot_count"), pick(qualityDims, "date", "category", "material"), qcPerm, false);
        add("oqc_pass_rate", "OQC 合格率", qc, Unit.PERCENT, "期间内判定的出货检验批次中判定合格的占比",
                Source.QUALITY, typed("OQC", "pass_count"), typed("OQC", "lot_count"), pick(qualityDims, "date", "customer", "category", "material"), qcPerm,
                false);
        add("iqc_lot_count", "来料检验批次", qc, Unit.COUNT, "期间内判定的来料检验批次数",
                Source.QUALITY, typed("IQC", "lot_count"), null, pick(qualityDims, "date", "supplier", "category", "material"), qcPerm, false);
        add("ncr_count", "NCR 数", qc, Unit.COUNT, "期间内登记的不合格品报告数",
                Source.QUALITY, "SUM(ncr_count)", null, pick(qualityDims, "date", "supplier", "customer", "category", "material"), qcPerm, false);
        add("complaint_count", "客诉数", qc, Unit.COUNT, "期间内登记的客诉数（不含已取消）",
                Source.QUALITY, "SUM(complaint_count)", null, pick(qualityDims, "date", "customer", "category", "material"), qcPerm, false);

        // ---------- 生产 ----------
        String mfg = "production";
        String mfgPerm = BiModuleConfig.PERM_PRODUCTION;
        add("production_output", "产量", mfg, Unit.QTY, "期间内合格完工入库数量",
                Source.PRODUCTION, "SUM(in_qty)", null, PRODUCTION_DIMS, mfgPerm, false);
        add("plan_qty", "计划数量", mfg, Unit.QTY, "计划完工日在期间内的已下达生产订单数量",
                Source.PRODUCTION, "SUM(plan_qty)", null, PRODUCTION_DIMS, mfgPerm, false);
        add("plan_achievement_rate", "计划达成率", mfg, Unit.PERCENT, "合格完工入库数量 ÷ 期间计划完工数量",
                Source.PRODUCTION, "SUM(in_qty)", "SUM(plan_qty)", pick(PRODUCTION_DIMS, "date", "dept"), mfgPerm, false);
        add("fpy", "直通率", mfg, Unit.PERCENT, "一次合格数量（正常报工合格数）÷ 报工投入数量（合格 + 不良 + 报废）",
                Source.PRODUCTION, "SUM(first_pass_qty)", "SUM(good_qty + defect_qty + scrap_qty)", PRODUCTION_DIMS, mfgPerm, false);
        add("yield_rate", "良率", mfg, Unit.PERCENT, "报工合格数量 ÷ 报工投入数量",
                Source.PRODUCTION, "SUM(good_qty)", "SUM(good_qty + defect_qty + scrap_qty)", PRODUCTION_DIMS, mfgPerm, false);
        add("work_hours", "工时", mfg, Unit.QTY, "期间内报工工时（小时）",
                Source.PRODUCTION, "SUM(work_hours)", null, PRODUCTION_DIMS, mfgPerm, false);
        add("delayed_order_count", "延期订单数", mfg, Unit.COUNT, "计划完工日在期间内、到期未完工或实际完工晚于计划的生产订单数",
                Source.PRODUCTION, "SUM(delayed_order_count)", null, pick(PRODUCTION_DIMS, "date", "dept", "category", "material"), mfgPerm, false);

        // ---------- 库存 ----------
        String inv = "inventory";
        String invPerm = BiModuleConfig.PERM_INVENTORY;
        add("inventory_amount", "库存金额", inv, Unit.AMOUNT, "截止日（每个时间段最后一个快照日）各仓库库存数量 × 参考单价",
                Source.INV_SNAPSHOT, "SUM(amount)", null, SNAPSHOT_DIMS, invPerm, false, 100, Mode.LAST);
        add("inventory_qty", "库存数量", inv, Unit.QTY, "截止日库存数量",
                Source.INV_SNAPSHOT, "SUM(qty)", null, SNAPSHOT_DIMS, invPerm, false, 100, Mode.LAST);
        add("inventory_avg_amount", "平均库存金额", inv, Unit.AMOUNT, "期间内各快照日库存金额的平均值",
                Source.INV_SNAPSHOT, "SUM(amount)", null, SNAPSHOT_DIMS, invPerm, false, 100, Mode.AVG);
        add("slow_moving_amount", "呆滞库存金额", inv, Unit.AMOUNT, "截止日超过参数天数无出库（从无出库按最近入库）的库存金额",
                Source.INV_SNAPSHOT, "SUM(CASE WHEN idle_days >= " + SLOW_DAYS + " THEN amount ELSE 0 END)", null, SNAPSHOT_DIMS, invPerm, false, 100, Mode.LAST);
        add("slow_moving_ratio", "呆滞占比", inv, Unit.PERCENT, "呆滞库存金额 ÷ 库存金额",
                Source.INV_SNAPSHOT, "SUM(CASE WHEN idle_days >= " + SLOW_DAYS + " THEN amount ELSE 0 END)", "SUM(amount)", SNAPSHOT_DIMS, invPerm, false, 100,
                Mode.LAST);
        add("aged_amount", "库龄 >180 天金额", inv, Unit.AMOUNT, "截止日最近入库距今超过 180 天的库存金额",
                Source.INV_SNAPSHOT, "SUM(CASE WHEN age_days > 180 THEN amount ELSE 0 END)", null, SNAPSHOT_DIMS, invPerm, false, 100, Mode.LAST);
        add("inventory_in_amount", "入库金额", inv, Unit.AMOUNT, "期间内入库金额（不含调拨）",
                Source.INV_MONTHLY, "SUM(in_amount)", null, MONTHLY_DIMS, invPerm, false);
        add("inventory_out_amount", "出库成本", inv, Unit.AMOUNT, "期间内出库金额（不含调拨）",
                Source.INV_MONTHLY, "SUM(out_amount)", null, MONTHLY_DIMS, invPerm, false);
        derived("inventory_turnover_days", "库存周转天数", inv, Unit.DAYS, "期间平均库存金额 ÷ 期间日均出库成本",
                List.of("inventory_avg_amount", "inventory_out_amount"), invPerm,
                (v, days) -> perDay(v.get("inventory_avg_amount"), v.get("inventory_out_amount"), days));

        // ---------- 财务 ----------
        String finTopic = "finance";
        add("ar_balance", "应收余额", finTopic, Unit.AMOUNT, "截止期间末未核销应收（本位币，按账面）",
                Source.FINANCE, partner("CUSTOMER", "end_balance"), null, AR_DIMS, fin, false, 100, Mode.LAST);
        add("ar_overdue", "逾期应收", finTopic, Unit.AMOUNT, "截止期间末已过到期日的未核销应收",
                Source.FINANCE, partner("CUSTOMER", "overdue_amount"), null, AR_DIMS, fin, false, 100, Mode.LAST);
        add("ar_overdue_ratio", "逾期占比", finTopic, Unit.PERCENT, "逾期应收 ÷ 应收余额",
                Source.FINANCE, partner("CUSTOMER", "overdue_amount"), partner("CUSTOMER", "end_balance"), AR_DIMS, fin, false, 100, Mode.LAST);
        add("ar_add_amount", "应收发生额", finTopic, Unit.AMOUNT, "期间内新增应收（含税本位币）",
                Source.FINANCE, partner("CUSTOMER", "add_amount"), null, AR_DIMS, fin, false);
        add("ap_balance", "应付余额", finTopic, Unit.AMOUNT, "截止期间末未核销应付",
                Source.FINANCE, partner("SUPPLIER", "end_balance"), null, AP_DIMS, fin, false, 100, Mode.LAST);
        add("ap_add_amount", "应付发生额", finTopic, Unit.AMOUNT, "期间内新增应付（含税本位币）",
                Source.FINANCE, partner("SUPPLIER", "add_amount"), null, AP_DIMS, fin, false);
        derived("dso", "应收周转天数（DSO）", finTopic, Unit.DAYS, "期末应收余额 ÷ 期间日均应收发生额",
                List.of("ar_balance", "ar_add_amount"), fin, (v, days) -> perDay(v.get("ar_balance"), v.get("ar_add_amount"), days));
        derived("dpo", "应付周转天数（DPO）", finTopic, Unit.DAYS, "期末应付余额 ÷ 期间日均应付发生额",
                List.of("ap_balance", "ap_add_amount"), fin, (v, days) -> perDay(v.get("ap_balance"), v.get("ap_add_amount"), days));
    }

    private MetricRegistry() {
    }

    public static Collection<MetricDefinition> all() {
        return METRICS.values();
    }

    public static Optional<MetricDefinition> find(String code) {
        return Optional.ofNullable(code == null ? null : METRICS.get(code));
    }

    public static MetricDefinition get(String code) {
        return METRICS.get(code);
    }

    // ==================== 注册工具 ====================

    private static void add(String code, String name, String topic, Unit unit, String desc, Source source, String num, String den,
                            Map<String, String> dims, String perm, boolean sensitive) {
        add(code, name, topic, unit, desc, source, num, den, dims, perm, sensitive, 100, Mode.SUM);
    }

    private static void add(String code, String name, String topic, Unit unit, String desc, Source source, String num, String den,
                            Map<String, String> dims, String perm, boolean sensitive, int factor) {
        add(code, name, topic, unit, desc, source, num, den, dims, perm, sensitive, factor, Mode.SUM);
    }

    private static void add(String code, String name, String topic, Unit unit, String desc, Source source, String num, String den,
                            Map<String, String> dims, String perm, boolean sensitive, int factor, Mode mode) {
        METRICS.put(code, new MetricDefinition(code, name, topic, unit, desc, source, num, den, den == null ? 1 : factor, mode, Map.copyOf(dims), perm,
                sensitive, List.of(), null));
    }

    private static void special(String code, String name, String topic, Unit unit, String desc, Map<String, String> dims, String perm) {
        METRICS.put(code, new MetricDefinition(code, name, topic, unit, desc, Source.SPECIAL, null, null, 1, Mode.SUM, Map.copyOf(dims), perm, false,
                List.of(), null));
    }

    private static void derived(String code, String name, String topic, Unit unit, String desc, List<String> deps, String perm,
                                BiFunction<Map<String, BigDecimal>, Integer, BigDecimal> fn) {
        Set<String> common = null;
        boolean sensitive = false;
        for (String d : deps) {
            MetricDefinition dep = METRICS.get(d);
            if (common == null) common = new LinkedHashSet<>(dep.dimColumns().keySet());
            else common.retainAll(dep.dimColumns().keySet());
            sensitive |= dep.sensitive();
        }
        Map<String, String> dims = new LinkedHashMap<>();
        if (common != null) common.forEach(d -> dims.put(d, ""));
        METRICS.put(code, new MetricDefinition(code, name, topic, unit, desc, Source.DERIVED, null, null, 1, Mode.SUM, dims, perm, sensitive,
                List.copyOf(deps), fn));
    }

    private static Map<String, String> pick(Map<String, String> dims, String... keys) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String k : keys) m.put(k, dims.get(k));
        return m;
    }

    private static String typed(String type, String column) {
        return "SUM(CASE WHEN inspect_type = '" + type + "' THEN " + column + " ELSE 0 END)";
    }

    private static String partner(String type, String column) {
        return "SUM(CASE WHEN partner_type = '" + type + "' THEN " + column + " ELSE 0 END)";
    }

    /** 余额 ÷ (发生额 ÷ 天数)；发生额为 0 时无意义 */
    static BigDecimal perDay(BigDecimal balance, BigDecimal flow, Integer days) {
        if (balance == null || flow == null || flow.signum() == 0 || days == null || days <= 0) return null;
        return balance.multiply(BigDecimal.valueOf(days)).divide(flow, 1, RoundingMode.HALF_UP);
    }

    /** 维度名称列表（页面展示） */
    public static List<String> dimensionLabels(MetricDefinition m) {
        List<String> list = new ArrayList<>();
        m.dimColumns().keySet().forEach(d -> list.add(DIMENSIONS.getOrDefault(d, d)));
        return list;
    }
}
