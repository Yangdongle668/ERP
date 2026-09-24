package com.erp.module.inventory.config;

import com.erp.framework.module.ErpModule;
import com.erp.module.system.api.coderule.CodeRuleDefinition;
import com.erp.module.system.api.coderule.CodeRuleDefinition.ResetCycle;
import com.erp.module.system.api.dict.DictDefinition;
import com.erp.module.system.api.param.ParamDefinition;
import com.erp.module.system.api.param.ParamDefinitions;
import com.erp.module.system.api.permission.PermissionDefinition;
import com.erp.module.system.api.print.PrintBizDefinition;
import com.erp.module.system.api.workflow.ApprovalBizDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 仓库模块的声明式注册（需求 08-仓库 README 第 7～12 节） */
@Configuration
public class InventoryModuleConfig {

    public static final String MODULE = "inventory";
    public static final String CODE_STOCK_IN = "INV_STOCK_IN";
    public static final String CODE_STOCK_OUT = "INV_STOCK_OUT";
    public static final String CODE_TRANSFER = "INV_TRANSFER";
    public static final String CODE_COUNT = "INV_COUNT";
    public static final String CODE_BATCH = "INV_BATCH";

    @Bean
    public ErpModule inventoryModule() {
        return new ErpModule(MODULE, "仓库", 120);
    }

    // ==================== 编码规则 ====================

    @Bean
    public CodeRuleDefinition stockInCodeRule() {
        return CodeRuleDefinition.of(CODE_STOCK_IN, "入库单", MODULE, "IN-", "yyyyMMdd", "-", 4, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition stockOutCodeRule() {
        return CodeRuleDefinition.of(CODE_STOCK_OUT, "出库单", MODULE, "OUT-", "yyyyMMdd", "-", 4, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition transferCodeRule() {
        return CodeRuleDefinition.of(CODE_TRANSFER, "调拨单", MODULE, "TF-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition countCodeRule() {
        return CodeRuleDefinition.of(CODE_COUNT, "盘点单", MODULE, "CK-", "yyyyMM", "-", 3, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition batchCodeRule() {
        return CodeRuleDefinition.of(CODE_BATCH, "批次号", MODULE, "B", "yyMMdd", "", 3, ResetCycle.DAY).manual(true);
    }

    // ==================== 权限点 ====================

    @Bean
    public PermissionDefinition stockPermissions() {
        return PermissionDefinition.group(MODULE, "stock", "库存查询", 10)
                .menu("inv:stock:query", "查看")
                .button("inv:stock:export", "导出")
                .button("inv:batch:freeze", "冻结/解冻批次")
                .button("inv:batch:update", "修改批次属性")
                .field("inv:stock:cost", "查看成本金额");
    }

    @Bean
    public PermissionDefinition warehousePermissions() {
        return PermissionDefinition.group(MODULE, "warehouse", "仓库与库位", 20)
                .menu("inv:warehouse:query", "查看")
                .button("inv:warehouse:create", "新建")
                .button("inv:warehouse:update", "编辑")
                .button("inv:warehouse:delete", "删除");
    }

    @Bean
    public PermissionDefinition stockInPermissions() {
        return docPermissions("in", "入库单", 30, true);
    }

    @Bean
    public PermissionDefinition stockOutPermissions() {
        return docPermissions("out", "出库单", 40, true);
    }

    @Bean
    public PermissionDefinition transferPermissions() {
        return PermissionDefinition.group(MODULE, "transfer", "调拨单", 50)
                .menu("inv:transfer:query", "查看")
                .button("inv:transfer:create", "新建")
                .button("inv:transfer:update", "编辑")
                .button("inv:transfer:delete", "删除")
                .button("inv:transfer:confirm", "确认调拨")
                .button("inv:transfer:unconfirm", "反确认")
                .button("inv:transfer:void", "作废")
                .button("inv:transfer:print", "打印");
    }

    @Bean
    public PermissionDefinition countPermissions() {
        return PermissionDefinition.group(MODULE, "count", "盘点", 60)
                .menu("inv:count:query", "查看")
                .button("inv:count:create", "新建")
                .button("inv:count:input", "录入实盘")
                .button("inv:count:submit", "提交")
                .button("inv:count:approve", "审核（查看账面数量）")
                .button("inv:count:void", "作废")
                .button("inv:count:print", "打印");
    }

    @Bean
    public PermissionDefinition periodPermissions() {
        return PermissionDefinition.group(MODULE, "period", "期初与月结", 90)
                .menu("inv:period:query", "查看")
                .button("inv:period:close", "月结")
                .button("inv:period:reopen", "反结账")
                .button("inv:opening:import", "期初导入");
    }

    private static PermissionDefinition docPermissions(String code, String name, int sort, boolean submit) {
        PermissionDefinition p = PermissionDefinition.group(MODULE, code, name, sort)
                .menu("inv:" + code + ":query", "查看")
                .button("inv:" + code + ":create", "新建")
                .button("inv:" + code + ":update", "编辑")
                .button("inv:" + code + ":delete", "删除");
        if (submit) p.button("inv:" + code + ":submit", "提交");
        return p.button("inv:" + code + ":confirm", "确认")
                .button("inv:" + code + ":unconfirm", "反确认")
                .button("inv:" + code + ":void", "作废")
                .button("inv:" + code + ":print", "打印")
                .button("inv:" + code + ":export", "导出");
    }

    // ==================== 字典 ====================

    @Bean
    public DictDefinition otherInReasonDict() {
        return DictDefinition.of("inv_other_in_reason", "其他入库原因", MODULE)
                .builtin("GIFT", "赠品", "Gift").builtin("CUSTOMER_SUPPLIED", "客供料", "Customer supplied")
                .builtin("FOUND", "盘点外发现", "Found").builtin("OTHER", "其他", "Other");
    }

    @Bean
    public DictDefinition otherOutReasonDict() {
        return DictDefinition.of("inv_other_out_reason", "其他出库原因", MODULE)
                .builtin("SCRAP", "报废", "Scrap").builtin("SAMPLE", "样品", "Sample").builtin("RD_USE", "研发领用", "R&D use")
                .builtin("DEPT_USE", "部门领用", "Department use").builtin("OTHER", "其他", "Other");
    }

    @Bean
    public DictDefinition countDiffReasonDict() {
        return DictDefinition.of("inv_count_diff_reason", "盘点差异原因", MODULE)
                .builtin("RECORD_ERROR", "单据漏录/错录", "Record error").builtin("DAMAGE", "损坏", "Damage")
                .builtin("LOSS", "丢失", "Loss").builtin("MEASURE", "计量误差", "Measure error").builtin("OTHER", "其他", "Other");
    }

    // ==================== 系统参数 ====================

    @Bean
    public ParamDefinitions inventoryParams() {
        return ParamDefinitions.of(
                ParamDefinition.bool("inv.stock.allow-negative", MODULE, "库存控制", "允许负库存", false,
                        "全局开关；仓库上还可单独设置，两者都允许时才允许负库存。建议关闭").sort(10),
                ParamDefinition.bool("inv.transfer.auto-confirm-inspection", MODULE, "库存控制", "检验调拨自动确认", false,
                        "是：检验判定后生成的调拨单自动过账；否：仓管员确认实物移库后过账").sort(20),
                ParamDefinition.bool("inv.in.auto-confirm-source", MODULE, "库存控制", "来源单据生成的入库单自动确认", false,
                        "小工厂可打开以减少操作；必填信息不全时仍需仓管员确认").sort(30),
                ParamDefinition.bool("inv.out.auto-confirm-source", MODULE, "库存控制", "来源单据生成的出库单自动确认", false,
                        "物料能按规则自动分配批次且库存充足时自动确认").sort(40),
                ParamDefinition.decimal("inv.count.recount-threshold-pct", MODULE, "盘点", "复盘阈值（差异比例 %）", "5", "0", "100",
                        "差异比例达到该值需要复盘").sort(10),
                ParamDefinition.decimal("inv.count.recount-threshold-amount", MODULE, "盘点", "复盘阈值（差异金额）", "1000", "0", null,
                        "差异金额（本位币）达到该值需要复盘；与比例任一满足即需复盘").sort(20),
                ParamDefinition.integer("inv.alert.slow-moving-days", MODULE, "预警", "呆滞天数", 180, 1, 3650, "超过该天数无出库视为呆滞").sort(10),
                ParamDefinition.integer("inv.alert.expiry-warn-days", MODULE, "预警", "临期提醒天数", 30, 1, 365, "到期前该天数开始提醒").sort(20),
                ParamDefinition.integer("inv.qc.overdue-hours", MODULE, "预警", "待检超时（小时）", 24, 1, 720, "待检仓库存超过该小时数未检验时预警").sort(30));
    }

    // ==================== 审批与打印 ====================

    @Bean
    public ApprovalBizDefinition otherInApproval() {
        return ApprovalBizDefinition.of("INV_OTHER_IN", "其他入库", MODULE, "/inventory/in/{id}")
                .dictField("reason", "原因", "inv_other_in_reason")
                .numberField("amountBase", "金额(本位币)");
    }

    @Bean
    public ApprovalBizDefinition otherOutApproval() {
        return ApprovalBizDefinition.of("INV_OTHER_OUT", "其他出库", MODULE, "/inventory/out/{id}")
                .dictField("reason", "原因", "inv_other_out_reason")
                .numberField("amountBase", "金额(本位币)");
    }

    @Bean
    public ApprovalBizDefinition countApproval() {
        return ApprovalBizDefinition.of("INV_COUNT", "盘点差异", MODULE, "/inventory/count/{id}")
                .numberField("diffAmountBase", "差异金额(绝对值合计)");
    }

    @Bean
    public PrintBizDefinition stockInPrint() {
        return docPrint("INV_STOCK_IN", "入库单", "/inventory/stock-ins/{id}/print-data");
    }

    @Bean
    public PrintBizDefinition stockOutPrint() {
        return docPrint("INV_STOCK_OUT", "出库单", "/inventory/stock-outs/{id}/print-data");
    }

    @Bean
    public PrintBizDefinition transferPrint() {
        return docPrint("INV_TRANSFER", "调拨单", "/inventory/transfers/{id}/print-data");
    }

    @Bean
    public PrintBizDefinition countPrint() {
        return PrintBizDefinition.of("INV_COUNT", "盘点表", MODULE, "/inventory/counts/{id}/print-data")
                .variable("docNo", "盘点单号", "string").variable("docDate", "日期", "date").variable("warehouseNames", "盘点仓库", "string")
                .variable("blindCount", "盲盘", "string").variable("lines", "明细", "array").variable("lines.lineNo", "行号", "number")
                .variable("lines.warehouseName", "仓库", "string").variable("lines.locationCode", "库位", "string")
                .variable("lines.code", "物料编码", "string").variable("lines.name", "名称", "string").variable("lines.spec", "规格", "string")
                .variable("lines.uom", "单位", "string").variable("lines.batchNo", "批次", "string").variable("lines.bookQty", "账面数量（盲盘为空）", "qty")
                .sampleData("{\"docNo\":\"CK-202609-001\",\"docDate\":\"2026-09-24\",\"warehouseNames\":\"电子料仓\",\"blindCount\":\"是\","
                        + "\"lines\":[{\"lineNo\":1,\"warehouseName\":\"电子料仓\",\"locationCode\":\"\",\"code\":\"ELEC00001\",\"name\":\"贴片电阻 10K\","
                        + "\"spec\":\"0603 1%\",\"uom\":\"PCS\",\"batchNo\":\"B260901001\",\"bookQty\":null}]}");
    }

    private static PrintBizDefinition docPrint(String bizType, String name, String api) {
        return PrintBizDefinition.of(bizType, name, MODULE, api)
                .variable("docNo", "单号", "string").variable("docDate", "日期", "date").variable("typeName", "类型", "string")
                .variable("warehouseName", "仓库", "string").variable("toWarehouseName", "调入仓（调拨）", "string")
                .variable("sourceNo", "来源单号", "string").variable("partnerName", "供应商/客户/领用部门", "string")
                .variable("reasonName", "原因", "string").variable("remark", "备注", "string").variable("statusName", "状态", "string")
                .variable("confirmedByName", "确认人", "string").variable("lines", "明细", "array").variable("lines.lineNo", "行号", "number")
                .variable("lines.code", "物料编码", "string").variable("lines.name", "名称", "string").variable("lines.spec", "规格", "string")
                .variable("lines.uom", "单位", "string").variable("lines.qty", "数量", "qty").variable("lines.batchNo", "批次", "string")
                .variable("lines.locationCode", "库位", "string").variable("lines.remark", "备注", "string").variable("totalQty", "总数量", "qty")
                .sampleData("{\"docNo\":\"IN-20260924-0001\",\"docDate\":\"2026-09-24\",\"typeName\":\"采购入库\",\"warehouseName\":\"电子料仓\","
                        + "\"toWarehouseName\":\"\",\"sourceNo\":\"AR-202609-0012\",\"partnerName\":\"深圳某某电子\",\"reasonName\":\"\",\"remark\":\"\","
                        + "\"statusName\":\"已入库\",\"confirmedByName\":\"张三\",\"totalQty\":1500,"
                        + "\"lines\":[{\"lineNo\":1,\"code\":\"ELEC00001\",\"name\":\"贴片电阻 10K\",\"spec\":\"0603 1%\",\"uom\":\"PCS\",\"qty\":1000,"
                        + "\"batchNo\":\"B260924001\",\"locationCode\":\"A-01-01-01\",\"remark\":\"\"},{\"lineNo\":2,\"code\":\"ELEC00002\",\"name\":\"贴片电容 100nF\","
                        + "\"spec\":\"0402 16V\",\"uom\":\"PCS\",\"qty\":500,\"batchNo\":\"B260924002\",\"locationCode\":\"A-01-01-02\",\"remark\":\"\"}]}");
    }
}
