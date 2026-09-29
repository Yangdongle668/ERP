package com.erp.module.production.config;

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

import java.util.List;

/** 生产模块的声明式注册（需求 09-生产 README 第 6～10、12 节） */
@Configuration
public class ProductionModuleConfig {

    public static final String MODULE = "production";

    // 编码规则 / 单据类型（审批、打印、附件、操作日志共用）
    public static final String PROD_ORDER = "MFG_PROD_ORDER";
    public static final String WORK_ORDER = "MFG_WORK_ORDER";
    public static final String ISSUE = "MFG_ISSUE";
    public static final String ISSUE_OVER = "MFG_ISSUE_OVER";
    public static final String RETURN = "MFG_RETURN";
    public static final String REPORT = "MFG_REPORT";
    public static final String FINISH = "MFG_FINISH";
    /** 不良记录（操作日志、附件照片） */
    public static final String DEFECT = "MFG_DEFECT";

    // 系统参数
    public static final String P_OVER_PRODUCE_PCT = "mfg.order.over-produce-pct";
    public static final String P_OVER_ISSUE_PCT = "mfg.issue.over-issue-pct";
    public static final String P_KIT_CHECK = "mfg.issue.kit-check";
    public static final String P_REQUIRE_WORK_ORDER = "mfg.report.require-work-order";
    public static final String P_AUTO_APPROVE = "mfg.report.auto-approve";
    public static final String P_CLOSE_REQUIRE_RETURN = "mfg.close.require-return";

    @Bean
    public ErpModule productionModule() {
        return new ErpModule(MODULE, "生产", 240);
    }

    // ==================== 编码规则 ====================

    @Bean
    public CodeRuleDefinition mfgProdOrderCodeRule() {
        return CodeRuleDefinition.of(PROD_ORDER, "生产订单", MODULE, "MO-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition mfgWorkOrderCodeRule() {
        return CodeRuleDefinition.of(WORK_ORDER, "工单", MODULE, "WO-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition mfgIssueCodeRule() {
        return CodeRuleDefinition.of(ISSUE, "领料单", MODULE, "MI-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition mfgReturnCodeRule() {
        return CodeRuleDefinition.of(RETURN, "退料单", MODULE, "MR-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition mfgReportCodeRule() {
        return CodeRuleDefinition.of(REPORT, "报工单", MODULE, "RP-", "yyyyMMdd", "-", 4, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition mfgFinishCodeRule() {
        return CodeRuleDefinition.of(FINISH, "完工入库申请", MODULE, "FN-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    // ==================== 权限点 ====================

    @Bean
    public PermissionDefinition mfgProdOrderPermissions() {
        return PermissionDefinition.group(MODULE, "prod-order", "生产订单", 10)
                .menu("mfg:prod-order:query", "查看")
                .button("mfg:prod-order:create", "新建")
                .button("mfg:prod-order:update", "编辑/调整用料")
                .button("mfg:prod-order:delete", "删除")
                .button("mfg:prod-order:submit", "提交")
                .button("mfg:prod-order:release", "下达")
                .button("mfg:prod-order:unrelease", "撤销下达")
                .button("mfg:prod-order:suspend", "暂停/恢复")
                .button("mfg:prod-order:close", "关闭")
                .button("mfg:prod-order:void", "作废")
                .button("mfg:prod-order:print", "打印");
    }

    @Bean
    public PermissionDefinition mfgWorkOrderPermissions() {
        return PermissionDefinition.group(MODULE, "work-order", "工单派工", 20)
                .menu("mfg:work-order:query", "查看")
                .button("mfg:work-order:create", "派工")
                .button("mfg:work-order:update", "完成/取消")
                .button("mfg:work-order:delete", "删除")
                .button("mfg:work-order:print", "打印");
    }

    @Bean
    public PermissionDefinition mfgIssuePermissions() {
        return PermissionDefinition.group(MODULE, "issue", "领料", 30)
                .menu("mfg:issue:query", "查看")
                .button("mfg:issue:create", "新建")
                .button("mfg:issue:update", "编辑")
                .button("mfg:issue:delete", "删除")
                .button("mfg:issue:submit", "提交/撤回")
                .button("mfg:issue:over", "超领申请")
                .button("mfg:issue:print", "打印");
    }

    @Bean
    public PermissionDefinition mfgReturnPermissions() {
        return PermissionDefinition.group(MODULE, "return", "退料", 40)
                .menu("mfg:return:query", "查看")
                .button("mfg:return:create", "新建")
                .button("mfg:return:update", "编辑")
                .button("mfg:return:delete", "删除")
                .button("mfg:return:submit", "提交/撤回")
                .button("mfg:return:print", "打印");
    }

    @Bean
    public PermissionDefinition mfgReportPermissions() {
        return PermissionDefinition.group(MODULE, "report", "报工", 50)
                .menu("mfg:report:query", "查看")
                .button("mfg:report:create", "新建")
                .button("mfg:report:update", "编辑")
                .button("mfg:report:delete", "删除")
                .button("mfg:report:approve", "审核")
                .button("mfg:report:unapprove", "反审核");
    }

    @Bean
    public PermissionDefinition mfgFinishPermissions() {
        return PermissionDefinition.group(MODULE, "finish", "完工入库", 60)
                .menu("mfg:finish:query", "查看")
                .button("mfg:finish:create", "申请入库")
                .button("mfg:finish:cancel", "取消");
    }

    @Bean
    public PermissionDefinition mfgDefectPermissions() {
        return PermissionDefinition.group(MODULE, "defect", "不良与良率", 70)
                .menu("mfg:defect:query", "查看")
                .button("mfg:defect:create", "登记")
                .button("mfg:defect:update", "返修/报废处置")
                .button("mfg:defect:to-ncr", "生成 NCR");
    }

    @Bean
    public PermissionDefinition mfgTracePermissions() {
        return PermissionDefinition.group(MODULE, "trace", "生产追溯", 80)
                .menu("mfg:trace:query", "查看");
    }

    @Bean
    public PermissionDefinition mfgReportCenterPermissions() {
        return PermissionDefinition.group(MODULE, "report-center", "生产报表", 90)
                .menu("mfg:report-center:query", "查看")
                .button("mfg:report-center:export", "导出");
    }

    // ==================== 字典 ====================

    @Bean
    public DictDefinition mfgDefectCodeDict() {
        return DictDefinition.of("mfg_defect_code", "不良代码", MODULE)
                .builtin("SHORT", "短路", "Short").builtin("COLD_SOLDER", "虚焊", "Cold solder").builtin("MISSING", "少件", "Missing")
                .builtin("SCRATCH", "划伤", "Scratch").builtin("DIMENSION", "尺寸不良", "Dimension").builtin("FUNCTION", "功能不良", "Function")
                .builtin("OTHER", "其他", "Other");
    }

    @Bean
    public DictDefinition mfgScrapReasonDict() {
        return DictDefinition.of("mfg_scrap_reason", "报废原因", MODULE)
                .builtin("PROCESS", "工艺", "Process").builtin("OPERATION", "操作", "Operation").builtin("MATERIAL", "来料", "Material")
                .builtin("EQUIPMENT", "设备", "Equipment").builtin("OTHER", "其他", "Other");
    }

    @Bean
    public DictDefinition mfgOverIssueReasonDict() {
        return DictDefinition.of("mfg_over_issue_reason", "超领原因", MODULE)
                .builtin("SCRAP", "损耗报废", "Scrap").builtin("DEFECT", "来料不良", "Defect").builtin("PLAN_ERROR", "用量错误", "Plan error")
                .builtin("OTHER", "其他", "Other");
    }

    @Bean
    public DictDefinition mfgShiftDict() {
        return DictDefinition.of("mfg_shift", "班次", MODULE)
                .builtin("DAY", "白班", "Day").builtin("NIGHT", "夜班", "Night");
    }

    // ==================== 系统参数 ====================

    @Bean
    public ParamDefinitions productionParams() {
        List<ParamDefinition.Option> levels = List.of(new ParamDefinition.Option("NONE", "不检查"), new ParamDefinition.Option("WARN", "警告"),
                new ParamDefinition.Option("BLOCK", "阻止"));
        return ParamDefinitions.of(
                ParamDefinition.decimal(P_OVER_PRODUCE_PCT, MODULE, "生产订单", "允许超产比例（%）", "0", "0", "100",
                        "首道报工点可报上限 = 订单数量 × (1 + 比例)").sort(10),
                ParamDefinition.decimal(P_OVER_ISSUE_PCT, MODULE, "领料", "正常领料允许超领比例（%）", "0", "0", "100",
                        "申请数量超过 未领 × (1 + 比例) 时需走超领单").sort(10),
                ParamDefinition.enumOf(P_KIT_CHECK, MODULE, "领料", "下达时齐套检查", "WARN", levels,
                        "按用料清单与可用库存（扣除其他已下达订单的未领需求）计算缺料").sort(20),
                ParamDefinition.bool(P_REQUIRE_WORK_ORDER, MODULE, "报工", "报工必须基于工单", false, "否：可直接对生产订单工序报工").sort(10),
                ParamDefinition.bool(P_AUTO_APPROVE, MODULE, "报工", "报工自动审核", true, "否：需班组长/主管审核后才计入数量").sort(20),
                ParamDefinition.enumOf(P_CLOSE_REQUIRE_RETURN, MODULE, "关闭", "关闭前必须退回余料", "WARN", levels,
                        "余料 = 已领 − 已退 − (合格 + 报废) × 单位用量").sort(10));
    }

    // ==================== 审批 ====================

    @Bean
    public ApprovalBizDefinition mfgProdOrderApproval() {
        return ApprovalBizDefinition.of(PROD_ORDER, "生产订单（手工新建）", MODULE, "/production/prod-order/{id}")
                .enumField("orderType", "订单类型", List.of(new ApprovalBizDefinition.Option("NORMAL", "标准"),
                        new ApprovalBizDefinition.Option("SAMPLE", "样品"), new ApprovalBizDefinition.Option("REWORK", "返工"),
                        new ApprovalBizDefinition.Option("DISASSEMBLY", "拆解")))
                .numberField("qty", "计划数量");
    }

    @Bean
    public ApprovalBizDefinition mfgIssueOverApproval() {
        return ApprovalBizDefinition.of(ISSUE_OVER, "超领单", MODULE, "/production/issue/{id}")
                .numberField("overPct", "超领比例(%)")
                .numberField("amountBase", "超领金额(本位币)");
    }

    // ==================== 打印 ====================

    @Bean
    public PrintBizDefinition mfgProdOrderPrint() {
        return PrintBizDefinition.of(PROD_ORDER, "生产订单流程卡", MODULE, "/production/prod-orders/{id}/print-data")
                .variable("docNo", "单号", "string").variable("orderTypeName", "类型", "string").variable("materialCode", "产品编码", "string")
                .variable("materialName", "产品名称", "string").variable("materialSpec", "规格", "string").variable("qty", "计划数量", "qty")
                .variable("uom", "单位", "string").variable("batchNo", "批次号", "string").variable("planStart", "计划开工", "date")
                .variable("planEnd", "计划完工", "date").variable("deptName", "车间", "string").variable("bomNo", "BOM", "string")
                .variable("salesOrderNo", "销售订单", "string").variable("remark", "备注", "string")
                .variable("materials", "用料", "array").variable("materials.lineNo", "行号", "number").variable("materials.code", "子件编码", "string")
                .variable("materials.name", "子件名称", "string").variable("materials.qtyPer", "单位用量", "qty").variable("materials.requiredQty", "应领", "qty")
                .variable("materials.issueMethod", "发料方式", "string")
                .variable("operations", "工序", "array").variable("operations.seq", "工序号", "number").variable("operations.operation", "工序", "string")
                .variable("operations.workCenterName", "工作中心", "string").variable("operations.barcode", "工序条码内容", "string")
                .sampleData("{\"docNo\":\"MO-202609-0001\",\"materialCode\":\"FG0001\",\"materialName\":\"控制板\",\"qty\":100,\"batchNo\":\"MO-202609-0001\","
                        + "\"materials\":[{\"lineNo\":1,\"code\":\"RM0001\",\"name\":\"PCBA\",\"qtyPer\":1,\"requiredQty\":100,\"issueMethod\":\"领料\"}],"
                        + "\"operations\":[{\"seq\":10,\"operation\":\"SMT\",\"workCenterName\":\"SMT1\",\"barcode\":\"MO-202609-0001#10\"}]}");
    }

    @Bean
    public PrintBizDefinition mfgWorkOrderPrint() {
        return PrintBizDefinition.of(WORK_ORDER, "派工单", MODULE, "/production/work-orders/{id}/print-data")
                .variable("docNo", "工单号", "string").variable("prodOrderNo", "生产订单", "string").variable("materialCode", "产品编码", "string")
                .variable("materialName", "产品名称", "string").variable("operationSeq", "工序号", "number").variable("operation", "工序", "string")
                .variable("workCenterName", "工作中心", "string").variable("planDate", "日期", "date").variable("shiftName", "班次", "string")
                .variable("planQty", "派工数量", "qty").variable("teamLeaderName", "班组长", "string").variable("remark", "作业要点", "string")
                .sampleData("{\"docNo\":\"WO-20260925-001\",\"prodOrderNo\":\"MO-202609-0001\",\"materialCode\":\"FG0001\",\"operationSeq\":10,"
                        + "\"operation\":\"SMT\",\"planQty\":300}");
    }

    @Bean
    public PrintBizDefinition mfgIssuePrint() {
        return PrintBizDefinition.of(ISSUE, "领料单", MODULE, "/production/issues/{id}/print-data")
                .variable("docNo", "单号", "string").variable("issueTypeName", "类型", "string").variable("docDate", "日期", "date")
                .variable("prodOrderNo", "生产订单", "string").variable("productCode", "产品编码", "string").variable("productName", "产品名称", "string")
                .variable("warehouseName", "发料仓", "string").variable("overReasonName", "超领原因", "string").variable("remark", "备注", "string")
                .variable("ownerName", "申请人", "string")
                .variable("lines", "明细", "array").variable("lines.lineNo", "行号", "number").variable("lines.materialCode", "物料编码", "string")
                .variable("lines.materialName", "名称", "string").variable("lines.materialSpec", "规格", "string").variable("lines.uom", "单位", "string")
                .variable("lines.requestQty", "申请数量", "qty").variable("lines.issuedQty", "实发", "qty")
                .sampleData("{\"docNo\":\"MI-20260925-001\",\"prodOrderNo\":\"MO-202609-0001\",\"lines\":[{\"lineNo\":1,\"materialCode\":\"RM0001\",\"requestQty\":100}]}");
    }

    @Bean
    public PrintBizDefinition mfgReturnPrint() {
        return PrintBizDefinition.of(RETURN, "退料单", MODULE, "/production/returns/{id}/print-data")
                .variable("docNo", "单号", "string").variable("returnTypeName", "类型", "string").variable("docDate", "日期", "date")
                .variable("prodOrderNo", "生产订单", "string").variable("productCode", "产品编码", "string").variable("warehouseName", "退入仓库", "string")
                .variable("remark", "备注", "string").variable("ownerName", "申请人", "string")
                .variable("lines", "明细", "array").variable("lines.lineNo", "行号", "number").variable("lines.materialCode", "物料编码", "string")
                .variable("lines.materialName", "名称", "string").variable("lines.uom", "单位", "string").variable("lines.qty", "退料数量", "qty")
                .variable("lines.batchNo", "批次", "string").variable("lines.defectDesc", "不良描述", "string")
                .sampleData("{\"docNo\":\"MR-20260925-001\",\"prodOrderNo\":\"MO-202609-0001\",\"lines\":[{\"lineNo\":1,\"materialCode\":\"RM0001\",\"qty\":5}]}");
    }
}
