package com.erp.module.quality.config;

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

/** 品质模块的声明式注册（需求 10-品质 README 第 6～12 节） */
@Configuration
public class QualityModuleConfig {

    public static final String MODULE = "quality";

    // 编码规则 / 单据类型（审批、打印、操作日志、附件共用）
    public static final String STANDARD = "QC_STANDARD";
    public static final String INSPECTION = "QC_INSPECTION";
    public static final String NCR = "QC_NCR";
    public static final String REJUDGE = "QC_REJUDGE";
    public static final String CAPA = "QC_CAPA";
    public static final String COMPLAINT = "QC_COMPLAINT";
    public static final String COMPLAINT_CLOSE = "QC_COMPLAINT_CLOSE";
    public static final String SCAR = "QC_SCAR";

    // 系统参数
    public static final String P_AUTO_NCR = "qc.iqc.auto-ncr";
    public static final String P_OVERDUE_HOURS = "qc.inspection.overdue-hours";
    public static final String P_FIRST_ARTICLE = "qc.ipqc.first-article";
    public static final String P_DEFECT_ALERT = "qc.defect.alert-threshold";
    public static final String P_CAPA_REPEAT = "qc.capa.trigger-repeat";
    public static final String P_COMPLAINT_REPLY_DAYS = "qc.complaint.reply-days";
    public static final String P_SCAR_REPLY_DAYS = "qc.scar.reply-days";
    public static final String P_MANAGERS = "qc.managers";
    public static final String P_EXECUTIVES = "qc.complaint.executives";

    @Bean
    public ErpModule qualityModule() {
        return new ErpModule(MODULE, "品质", 250);
    }

    // ==================== 编码规则 ====================

    @Bean
    public CodeRuleDefinition qcIqcCodeRule() {
        return CodeRuleDefinition.of("QC_IQC", "IQC 检验单", MODULE, "IQC-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition qcIpqcCodeRule() {
        return CodeRuleDefinition.of("QC_IPQC", "IPQC 检验单", MODULE, "IPQC-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition qcFqcCodeRule() {
        return CodeRuleDefinition.of("QC_FQC", "FQC 检验单", MODULE, "FQC-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition qcOqcCodeRule() {
        return CodeRuleDefinition.of("QC_OQC", "OQC 检验单", MODULE, "OQC-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition qcReturnCodeRule() {
        return CodeRuleDefinition.of("QC_RETURN", "退货检验单", MODULE, "RI-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition qcRecheckCodeRule() {
        return CodeRuleDefinition.of("QC_RECHECK", "复检单", MODULE, "RE-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition qcNcrCodeRule() {
        return CodeRuleDefinition.of(NCR, "NCR", MODULE, "NCR-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition qcCapaCodeRule() {
        return CodeRuleDefinition.of(CAPA, "CAPA", MODULE, "CAPA-", "yyyyMM", "-", 3, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition qcComplaintCodeRule() {
        return CodeRuleDefinition.of(COMPLAINT, "客诉", MODULE, "CC-", "yyyyMM", "-", 3, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition qcScarCodeRule() {
        return CodeRuleDefinition.of(SCAR, "SCAR", MODULE, "SCAR-", "yyyyMM", "-", 3, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition qcStandardCodeRule() {
        return CodeRuleDefinition.of(STANDARD, "检验标准", MODULE, "QS-", "", "", 4, ResetCycle.NEVER);
    }

    // ==================== 权限点 ====================

    @Bean
    public PermissionDefinition qcStandardPermissions() {
        return PermissionDefinition.group(MODULE, "standard", "检验标准", 10)
                .menu("qc:standard:query", "查看")
                .button("qc:standard:create", "新建")
                .button("qc:standard:update", "编辑")
                .button("qc:standard:approve", "生效")
                .button("qc:standard:delete", "删除/作废")
                .button("qc:sampling:manage", "抽样方案维护")
                .button("qc:defect-code:manage", "缺陷代码与项目库维护");
    }

    @Bean
    public PermissionDefinition qcIqcPermissions() {
        return inspectionGroup("iqc", "IQC 来料检验", 20);
    }

    @Bean
    public PermissionDefinition qcIpqcPermissions() {
        return inspectionGroup("ipqc", "IPQC 制程检验", 30);
    }

    @Bean
    public PermissionDefinition qcFqcPermissions() {
        return inspectionGroup("fqc", "FQC 成品检验", 40);
    }

    @Bean
    public PermissionDefinition qcOqcPermissions() {
        return inspectionGroup("oqc", "OQC 出货检验", 50);
    }

    @Bean
    public PermissionDefinition qcReturnPermissions() {
        return inspectionGroup("return", "退货检验", 60);
    }

    @Bean
    public PermissionDefinition qcInspectionPermissions() {
        return PermissionDefinition.group(MODULE, "inspection", "检验单通用", 65)
                .button("qc:inspection:create", "手工新建（IPQC/复检）")
                .button("qc:inspection:rejudge", "重判");
    }

    private static PermissionDefinition inspectionGroup(String code, String name, int sort) {
        return PermissionDefinition.group(MODULE, code, name, sort)
                .menu("qc:" + code + ":query", "查看")
                .button("qc:" + code + ":inspect", "录入")
                .button("qc:" + code + ":judge", "判定");
    }

    @Bean
    public PermissionDefinition qcNcrPermissions() {
        return PermissionDefinition.group(MODULE, "ncr", "NCR", 70)
                .menu("qc:ncr:query", "查看")
                .button("qc:ncr:create", "新建")
                .button("qc:ncr:update", "编辑/处置执行")
                .button("qc:ncr:submit", "提交 MRB")
                .button("qc:ncr:close", "关闭")
                .button("qc:ncr:void", "作废")
                .button("qc:ncr:print", "打印");
    }

    @Bean
    public PermissionDefinition qcCapaPermissions() {
        return PermissionDefinition.group(MODULE, "capa", "CAPA", 80)
                .menu("qc:capa:query", "查看")
                .button("qc:capa:create", "新建")
                .button("qc:capa:update", "编辑步骤")
                .button("qc:capa:verify", "效果验证")
                .button("qc:capa:close", "结案/取消");
    }

    @Bean
    public PermissionDefinition qcComplaintPermissions() {
        return PermissionDefinition.group(MODULE, "complaint", "客诉", 90)
                .menu("qc:complaint:query", "查看")
                .button("qc:complaint:create", "登记")
                .button("qc:complaint:update", "编辑/分析/处理结果")
                .button("qc:complaint:reply", "记录回复")
                .button("qc:complaint:close", "结案/取消");
    }

    @Bean
    public PermissionDefinition qcScarPermissions() {
        return PermissionDefinition.group(MODULE, "scar", "SCAR", 100)
                .menu("qc:scar:query", "查看")
                .button("qc:scar:create", "新建")
                .button("qc:scar:update", "编辑/登记回复")
                .button("qc:scar:send", "发出")
                .button("qc:scar:verify", "验证")
                .button("qc:scar:close", "取消");
    }

    @Bean
    public PermissionDefinition qcTracePermissions() {
        return PermissionDefinition.group(MODULE, "trace", "质量追溯与报表", 110)
                .menu("qc:trace:query", "质量追溯")
                .menu("qc:report:query", "质量报表")
                .button("qc:report:export", "导出");
    }

    // ==================== 字典 ====================

    @Bean
    public DictDefinition qcDefectCategoryDict() {
        return DictDefinition.of("qc_defect_category", "缺陷分类", MODULE)
                .builtin("APPEARANCE", "外观", "Appearance").builtin("DIMENSION", "尺寸", "Dimension").builtin("FUNCTION", "功能", "Function")
                .builtin("PERFORMANCE", "性能", "Performance").builtin("PACKAGING", "包装", "Packaging").builtin("LABEL", "标识", "Label")
                .builtin("DOCUMENT", "资料", "Document");
    }

    @Bean
    public DictDefinition qcInspectionMethodDict() {
        return DictDefinition.of("qc_inspection_method", "检验方法", MODULE)
                .builtin("VISUAL", "目视", "Visual").builtin("MEASURE", "量测", "Measure").builtin("TEST", "功能测试", "Test")
                .builtin("REPORT", "查验报告", "Report");
    }

    @Bean
    public DictDefinition qcComplaintTypeDict() {
        return DictDefinition.of("qc_complaint_type", "客诉类型", MODULE)
                .builtin("QUALITY", "质量", "Quality").builtin("DELIVERY", "交付", "Delivery").builtin("PACKAGING", "包装", "Packaging")
                .builtin("SERVICE", "服务", "Service").builtin("OTHER", "其他", "Other");
    }

    @Bean
    public DictDefinition qcNcrResponsibilityDict() {
        return DictDefinition.of("qc_ncr_responsibility", "责任归属", MODULE)
                .builtin("SUPPLIER", "供应商", "Supplier").builtin("PROCESS", "制程", "Process").builtin("DESIGN", "设计", "Design")
                .builtin("CUSTOMER", "客户", "Customer").builtin("LOGISTICS", "物流", "Logistics").builtin("UNKNOWN", "待确认", "Unknown");
    }

    // ==================== 系统参数 ====================

    @Bean
    public ParamDefinitions qcParams() {
        return ParamDefinitions.of(
                ParamDefinition.bool(P_AUTO_NCR, MODULE, "检验", "判定不合格自动生成 NCR", true, "判定拒收时自动生成草稿 NCR").sort(10),
                ParamDefinition.integer(P_OVERDUE_HOURS, MODULE, "检验", "检验超时（小时）", 24, 1, 720, "检验单创建后超时未判定提醒品质主管").sort(20),
                ParamDefinition.bool(P_FIRST_ARTICLE, MODULE, "检验", "生产订单首次报工需首件检验", false, "首件检验合格前阻止报工").sort(30),
                ParamDefinition.userList(P_MANAGERS, MODULE, "检验", "品质主管", "", "超时、超期、致命问题的提醒对象；为空时通知拥有“NCR 关闭”权限的用户").sort(40),
                ParamDefinition.integer(P_DEFECT_ALERT, MODULE, "制程", "同一不良当日预警阈值（件）", 10, 1, 100000, "").sort(10),
                ParamDefinition.integer(P_CAPA_REPEAT, MODULE, "CAPA", "触发 CAPA 的重复次数", 3, 1, 100, "同物料同缺陷 30 天内 NCR 次数").sort(10),
                ParamDefinition.integer(P_COMPLAINT_REPLY_DAYS, MODULE, "客诉", "客诉回复期限（天）", 3, 1, 60, "初步回复（D3 围堵）期限").sort(10),
                ParamDefinition.userList(P_EXECUTIVES, MODULE, "客诉", "致命客诉通知的管理层", "", "如总经理").sort(20),
                ParamDefinition.integer(P_SCAR_REPLY_DAYS, MODULE, "SCAR", "供应商回复期限（天）", 7, 1, 90, "").sort(10));
    }

    // ==================== 审批 ====================

    @Bean
    public ApprovalBizDefinition qcNcrApproval() {
        return ApprovalBizDefinition.of(NCR, "NCR（MRB 处置）", MODULE, "/quality/ncr/{id}")
                .enumField("disposition", "处置（含特采为 CONCESSION）", List.of(new ApprovalBizDefinition.Option("CONCESSION", "含特采"),
                        new ApprovalBizDefinition.Option("RETURN", "退供应商"), new ApprovalBizDefinition.Option("SORT", "挑选"),
                        new ApprovalBizDefinition.Option("REWORK", "返工"), new ApprovalBizDefinition.Option("SCRAP", "报废")))
                .numberField("qty", "不合格数量")
                .numberField("amountBase", "涉及金额(本位币)")
                .enumField("source", "来源", List.of(new ApprovalBizDefinition.Option("IQC", "来料检验"), new ApprovalBizDefinition.Option("IPQC", "制程检验"),
                        new ApprovalBizDefinition.Option("FQC", "成品检验"), new ApprovalBizDefinition.Option("OQC", "出货检验"),
                        new ApprovalBizDefinition.Option("RETURN", "退货检验"), new ApprovalBizDefinition.Option("RECHECK", "复检"),
                        new ApprovalBizDefinition.Option("PRODUCTION", "生产不良"), new ApprovalBizDefinition.Option("INVENTORY", "库存"),
                        new ApprovalBizDefinition.Option("COMPLAINT", "客诉")))
                .userField("ownerId", "QE");
    }

    @Bean
    public ApprovalBizDefinition qcRejudgeApproval() {
        return ApprovalBizDefinition.of(REJUDGE, "检验重判", MODULE, "/quality/inspection/{id}")
                .enumField("inspectType", "检验类型", List.of(new ApprovalBizDefinition.Option("IQC", "IQC"), new ApprovalBizDefinition.Option("IPQC", "IPQC"),
                        new ApprovalBizDefinition.Option("FQC", "FQC"), new ApprovalBizDefinition.Option("OQC", "OQC"),
                        new ApprovalBizDefinition.Option("RETURN", "退货检验"), new ApprovalBizDefinition.Option("RECHECK", "复检")));
    }

    @Bean
    public ApprovalBizDefinition qcComplaintCloseApproval() {
        return ApprovalBizDefinition.of(COMPLAINT_CLOSE, "客诉结案", MODULE, "/quality/complaint/{id}")
                .enumField("severity", "严重度", List.of(new ApprovalBizDefinition.Option("CRITICAL", "致命"), new ApprovalBizDefinition.Option("MAJOR", "严重"),
                        new ApprovalBizDefinition.Option("MINOR", "轻微")))
                .userField("qeId", "负责 QE");
    }

    // ==================== 打印 ====================

    @Bean
    public PrintBizDefinition qcInspectionPrint() {
        return PrintBizDefinition.of(INSPECTION, "检验报告", MODULE, "/quality/inspections/{id}/print-data")
                .variable("docNo", "单号", "string").variable("inspectTypeName", "检验类型", "string").variable("status", "状态", "string")
                .variable("materialCode", "物料编码", "string").variable("materialName", "物料名称", "string").variable("materialSpec", "规格", "string")
                .variable("batchNo", "批次", "string").variable("lotQty", "批量", "qty").variable("sampleQty", "样本量", "number")
                .variable("samplingText", "抽样方案", "string").variable("partnerName", "供应商/客户", "string").variable("upstreamNo", "上游单号", "string")
                .variable("standardText", "检验标准", "string").variable("crCount", "CR", "number").variable("maCount", "MA", "number")
                .variable("miCount", "MI", "number").variable("resultName", "结果", "string").variable("qualifiedQty", "合格数", "qty")
                .variable("concessionQty", "特采数", "qty").variable("rejectedQty", "不合格数", "qty").variable("inspectorName", "检验员", "string")
                .variable("judgeName", "判定人", "string").variable("judgeAt", "判定时间", "datetime")
                .variable("items", "检验项目", "array").variable("items.seq", "序号", "number").variable("items.itemName", "项目", "string")
                .variable("items.spec", "规格", "string").variable("items.levelName", "等级", "string").variable("items.sampleQty", "样本", "number")
                .variable("items.valuesText", "实测", "string").variable("items.ngCount", "不良数", "number").variable("items.itemResult", "结果", "string")
                .variable("defects", "缺陷", "array").variable("defects.defectCode", "代码", "string").variable("defects.defectName", "名称", "string")
                .variable("defects.defectLevel", "等级", "string").variable("defects.qty", "数量", "number").variable("defects.description", "描述", "string")
                .sampleData("{\"docNo\":\"IQC-20260924-001\",\"inspectTypeName\":\"来料检验\",\"materialCode\":\"ELEC00012\",\"materialName\":\"贴片电阻\","
                        + "\"lotQty\":5000,\"sampleQty\":200,\"resultName\":\"合格\",\"items\":[{\"seq\":1,\"itemName\":\"外观\",\"spec\":\"无破损\",\"ngCount\":0,\"itemResult\":\"OK\"}]}");
    }

    @Bean
    public PrintBizDefinition qcNcrPrint() {
        return PrintBizDefinition.of(NCR, "NCR 不合格品报告", MODULE, "/quality/ncrs/{id}/print-data")
                .variable("docNo", "单号", "string").variable("docDate", "日期", "date").variable("status", "状态", "string")
                .variable("sourceName", "来源", "string").variable("sourceNo", "来源单号", "string").variable("materialCode", "物料编码", "string")
                .variable("materialName", "物料名称", "string").variable("batchNo", "批次", "string").variable("ncrQty", "不合格数量", "qty")
                .variable("partnerName", "供应商/客户", "string").variable("severityName", "严重度", "string").variable("responsibilityName", "责任", "string")
                .variable("defectDescription", "不合格描述", "string").variable("containment", "围堵措施", "string").variable("ownerName", "QE", "string")
                .variable("dispositions", "处置", "array").variable("dispositions.dispositionName", "处置方式", "string")
                .variable("dispositions.qty", "数量", "qty").variable("dispositions.remark", "说明", "string").variable("dispositions.followDocNo", "后续单据", "string")
                .sampleData("{\"docNo\":\"NCR-202609-0001\",\"materialCode\":\"ELEC00012\",\"ncrQty\":5000,\"dispositions\":[{\"dispositionName\":\"特采\",\"qty\":4000}]}");
    }

    @Bean
    public PrintBizDefinition qcCapaPrint() {
        return PrintBizDefinition.of(CAPA, "8D 报告", MODULE, "/quality/capas/{id}/print-data")
                .variable("docNo", "单号", "string").variable("title", "标题", "string").variable("sourceNo", "来源", "string")
                .variable("customerName", "客户", "string").variable("supplierName", "供应商", "string").variable("materialCode", "物料", "string")
                .variable("leaderName", "负责人", "string").variable("teamText", "小组成员", "string").variable("dueDate", "期限", "date")
                .variable("d1Team", "D1", "string").variable("d2Problem", "D2", "string").variable("d3Containment", "D3", "string")
                .variable("d4RootCause", "D4", "string").variable("d5Actions", "D5", "string").variable("d6Implementation", "D6", "string")
                .variable("d7Prevention", "D7", "string").variable("d8Summary", "D8", "string").variable("verifyResultName", "验证结果", "string")
                .sampleData("{\"docNo\":\"CAPA-202609-001\",\"title\":\"电阻阻值超差\",\"leaderName\":\"张 QE\"}");
    }

    @Bean
    public PrintBizDefinition qcScarPrint() {
        return PrintBizDefinition.of(SCAR, "SCAR", MODULE, "/quality/scars/{id}/print-data")
                .variable("docNo", "单号", "string").variable("supplierName", "供应商", "string").variable("materialCode", "物料编码", "string")
                .variable("materialName", "物料名称", "string").variable("batchNo", "批次", "string").variable("problemDescription", "问题描述", "string")
                .variable("requirement", "要求", "string").variable("replyDueDate", "回复期限", "date").variable("sentAt", "发出时间", "datetime")
                .variable("ownerName", "SQE", "string")
                .sampleData("{\"docNo\":\"SCAR-202609-001\",\"supplierName\":\"华强电子\",\"requirement\":\"7 天内回复 8D 报告\"}");
    }
}
