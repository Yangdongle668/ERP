package com.erp.module.purchase.config;

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

/** 资材模块的声明式注册（需求 07-资材 README 第 6～10、12 节） */
@Configuration
public class PurchaseModuleConfig {

    public static final String MODULE = "purchase";

    // 编码规则 / 单据类型（审批、打印、附件、操作日志共用）
    public static final String SUPPLIER = "PUR_SUPPLIER";
    public static final String SUPPLIER_QUALIFY = "PUR_SUPPLIER_QUALIFY";
    public static final String PRICE_ADJUST = "PUR_PRICE_ADJUST";
    public static final String REQUISITION = "PUR_REQUISITION";
    public static final String RFQ = "PUR_RFQ";
    public static final String ORDER = "PUR_ORDER";
    public static final String ORDER_CHANGE = "PUR_ORDER_CHANGE";
    public static final String RECEIPT = "PUR_RECEIPT";
    public static final String OUTSOURCING = "PUR_OUTSOURCING";
    public static final String RETURN = "PUR_RETURN";
    public static final String STATEMENT = "PUR_STATEMENT";
    public static final String SCORE = "PUR_SCORE";

    // 系统参数
    public static final String P_REQUIRE_PRICE = "pur.order.require-price";
    public static final String P_PRICE_OVERRUN_PCT = "pur.order.price-overrun-pct";
    public static final String P_REQUIRE_SUPPLIER_MATERIAL = "pur.order.require-approved-supplier-material";
    public static final String P_OVER_RECEIVE_PCT = "pur.receipt.over-receive-default-pct";
    public static final String P_MIN_LIFE_CHECK = "pur.receipt.min-remaining-life-check";
    public static final String P_ONTIME_TOLERANCE = "pur.delivery.ontime-tolerance-days";
    public static final String P_SCORE_WEIGHTS = "pur.score.weights";
    public static final String P_SCORE_AUTO = "pur.score.auto-calculate";
    public static final String P_OVERDUE_REMIND_DAYS = "pur.overdue.remind-days";
    public static final String P_MRP_AUTO_SUBMIT = "pur.requisition.mrp-auto-submit";
    public static final String P_PRICE_OTHER_MATERIAL = "pur.price.allow-other-material";
    public static final String P_OVER_ISSUE_PCT = "pur.outsourcing.over-issue-pct";

    /** 采购价格字段权限（全模块生效） */
    public static final String PRICE_VIEW = "pur:price:view";

    @Bean
    public ErpModule purchaseModule() {
        return new ErpModule(MODULE, "资材", 220);
    }

    // ==================== 编码规则 ====================

    @Bean
    public CodeRuleDefinition purSupplierCodeRule() {
        return CodeRuleDefinition.of(SUPPLIER, "供应商编码", MODULE, "V", "", "", 5, ResetCycle.NEVER).manual(true);
    }

    @Bean
    public CodeRuleDefinition purRequisitionCodeRule() {
        return CodeRuleDefinition.of(REQUISITION, "采购申请", MODULE, "PR-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition purRfqCodeRule() {
        return CodeRuleDefinition.of(RFQ, "询价单", MODULE, "RFQ-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition purOrderCodeRule() {
        return CodeRuleDefinition.of(ORDER, "采购订单", MODULE, "PO-", "yyyyMMdd", "-", 4, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition purReceiptCodeRule() {
        return CodeRuleDefinition.of(RECEIPT, "到货单", MODULE, "RC-", "yyyyMMdd", "-", 4, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition purOutsourcingCodeRule() {
        return CodeRuleDefinition.of(OUTSOURCING, "委外单", MODULE, "OS-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition purReturnCodeRule() {
        return CodeRuleDefinition.of(RETURN, "采购退货单", MODULE, "RT-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition purStatementCodeRule() {
        return CodeRuleDefinition.of(STATEMENT, "供应商对账单", MODULE, "ST-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition purPriceAdjustCodeRule() {
        return CodeRuleDefinition.of(PRICE_ADJUST, "调价单", MODULE, "PA-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition purOrderChangeCodeRule() {
        return CodeRuleDefinition.of(ORDER_CHANGE, "采购订单变更单", MODULE, "PC-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    // ==================== 权限点 ====================

    @Bean
    public PermissionDefinition purSupplierPermissions() {
        return PermissionDefinition.group(MODULE, "supplier", "供应商", 10)
                .menu("pur:supplier:query", "查看")
                .button("pur:supplier:create", "新建")
                .button("pur:supplier:update", "编辑")
                .button("pur:supplier:qualify", "提交准入")
                .button("pur:supplier:suspend", "暂停/恢复")
                .button("pur:supplier:eliminate", "淘汰")
                .button("pur:supplier:delete", "删除")
                .button("pur:supplier:import", "导入")
                .button("pur:supplier:export", "导出");
    }

    @Bean
    public PermissionDefinition purPricePermissions() {
        return PermissionDefinition.group(MODULE, "price", "采购价格", 20)
                .menu("pur:price:query", "查看")
                .button("pur:price:adjust", "新建调价单")
                .button("pur:price:submit", "提交调价单")
                .button("pur:price:import", "导入")
                .button("pur:price:export", "导出")
                .field(PRICE_VIEW, "查看采购单价与金额（全模块）");
    }

    @Bean
    public PermissionDefinition purRequisitionPermissions() {
        return PermissionDefinition.group(MODULE, "requisition", "采购申请", 30)
                .menu("pur:requisition:query", "查看")
                .button("pur:requisition:create", "新建")
                .button("pur:requisition:update", "编辑")
                .button("pur:requisition:delete", "删除/作废")
                .button("pur:requisition:submit", "提交")
                .button("pur:requisition:unapprove", "反审核")
                .button("pur:requisition:close", "关闭")
                .button("pur:requisition:to-order", "转订单");
    }

    @Bean
    public PermissionDefinition purRfqPermissions() {
        return PermissionDefinition.group(MODULE, "rfq", "询价", 40)
                .menu("pur:rfq:query", "查看")
                .button("pur:rfq:create", "新建")
                .button("pur:rfq:update", "编辑/发出/取消")
                .button("pur:rfq:delete", "删除")
                .button("pur:rfq:quote", "录入报价")
                .button("pur:rfq:award", "定标");
    }

    @Bean
    public PermissionDefinition purOrderPermissions() {
        return PermissionDefinition.group(MODULE, "order", "采购订单", 50)
                .menu("pur:order:query", "查看")
                .button("pur:order:create", "新建")
                .button("pur:order:update", "编辑")
                .button("pur:order:delete", "删除")
                .button("pur:order:submit", "提交")
                .button("pur:order:unapprove", "反审核")
                .button("pur:order:change", "变更")
                .button("pur:order:close", "关闭")
                .button("pur:order:void", "作废")
                .button("pur:order:confirm-date", "回复交期")
                .button("pur:order:print", "打印/发送")
                .button("pur:order:export", "导出");
    }

    @Bean
    public PermissionDefinition purReceiptPermissions() {
        return PermissionDefinition.group(MODULE, "receipt", "到货", 60)
                .menu("pur:receipt:query", "查看")
                .button("pur:receipt:create", "新建")
                .button("pur:receipt:update", "编辑")
                .button("pur:receipt:delete", "删除")
                .button("pur:receipt:approve", "审核")
                .button("pur:receipt:unapprove", "反审核")
                .button("pur:receipt:print", "打印");
    }

    @Bean
    public PermissionDefinition purOutsourcingPermissions() {
        return PermissionDefinition.group(MODULE, "outsourcing", "委外", 70)
                .menu("pur:outsourcing:query", "查看")
                .button("pur:outsourcing:create", "新建")
                .button("pur:outsourcing:update", "编辑")
                .button("pur:outsourcing:delete", "删除/作废")
                .button("pur:outsourcing:submit", "提交/反审核")
                .button("pur:outsourcing:issue", "发料/余料退回")
                .button("pur:outsourcing:receive", "收货/核销")
                .button("pur:outsourcing:close", "关闭")
                .button("pur:outsourcing:print", "打印");
    }

    @Bean
    public PermissionDefinition purReturnPermissions() {
        return PermissionDefinition.group(MODULE, "return", "采购退货", 80)
                .menu("pur:return:query", "查看")
                .button("pur:return:create", "新建")
                .button("pur:return:update", "编辑")
                .button("pur:return:delete", "删除")
                .button("pur:return:submit", "提交")
                .button("pur:return:void", "作废")
                .button("pur:return:print", "打印");
    }

    @Bean
    public PermissionDefinition purStatementPermissions() {
        return PermissionDefinition.group(MODULE, "statement", "对账", 90)
                .menu("pur:statement:query", "查看")
                .button("pur:statement:create", "新建")
                .button("pur:statement:update", "编辑/反审核")
                .button("pur:statement:delete", "删除/作废")
                .button("pur:statement:submit", "提交")
                .button("pur:statement:confirm", "供应商确认")
                .button("pur:statement:unconfirm", "取消确认")
                .button("pur:statement:print", "打印");
    }

    @Bean
    public PermissionDefinition purScorePermissions() {
        return PermissionDefinition.group(MODULE, "score", "供应商评估", 100)
                .menu("pur:score:query", "查看")
                .button("pur:score:calculate", "计算/发布")
                .button("pur:score:update", "手工评分/撤销发布");
    }

    @Bean
    public PermissionDefinition purReportPermissions() {
        return PermissionDefinition.group(MODULE, "report", "采购报表", 110)
                .menu("pur:report:query", "查看")
                .button("pur:report:export", "导出");
    }

    // ==================== 字典 ====================

    @Bean
    public DictDefinition purSupplierTypeDict() {
        return DictDefinition.of("pur_supplier_type", "供应商类型", MODULE)
                .builtin("MANUFACTURER", "生产商", "Manufacturer").builtin("TRADER", "贸易商", "Trader")
                .builtin("OUTSOURCER", "委外加工商", "Outsourcer").builtin("SERVICE", "服务商", "Service");
    }

    @Bean
    public DictDefinition purSupplierLevelDict() {
        return DictDefinition.of("pur_supplier_level", "供应商等级", MODULE)
                .add(new DictDefinition.Item("A", "A", "A", DictDefinition.TagType.SUCCESS, true, false))
                .add(new DictDefinition.Item("B", "B", "B", DictDefinition.TagType.PRIMARY, true, false))
                .add(new DictDefinition.Item("C", "C", "C", DictDefinition.TagType.WARNING, true, true))
                .add(new DictDefinition.Item("D", "D", "D", DictDefinition.TagType.DANGER, true, false));
    }

    @Bean
    public DictDefinition purCertTypeDict() {
        return DictDefinition.of("pur_cert_type", "供应商资质类型", MODULE)
                .builtin("LICENSE", "营业执照", "Business License").builtin("ISO9001", "ISO9001", "ISO9001")
                .builtin("ISO14001", "ISO14001", "ISO14001").builtin("IATF16949", "IATF16949", "IATF16949")
                .builtin("ROHS_REPORT", "环保报告", "RoHS Report").builtin("OTHER", "其他", "Other");
    }

    @Bean
    public DictDefinition purRequisitionTypeDict() {
        return DictDefinition.of("pur_requisition_type", "申请类型", MODULE)
                .builtin("MRP", "计划申请", "MRP").builtin("MANUAL", "手工申请", "Manual")
                .builtin("SAMPLE", "样品", "Sample").builtin("EXPENSE", "费用类", "Expense");
    }

    @Bean
    public DictDefinition purReturnReasonDict() {
        return DictDefinition.of("pur_return_reason", "退货原因", MODULE)
                .builtin("IQC_REJECT", "检验不合格", "IQC reject").builtin("STOCK_DEFECT", "库存不良", "Stock defect")
                .builtin("OVER_RECEIPT", "多收", "Over receipt").builtin("PRODUCTION_DEFECT", "制程发现来料不良", "Production defect")
                .builtin("OTHER", "其他", "Other");
    }

    // ==================== 系统参数 ====================

    @Bean
    public ParamDefinitions purchaseParams() {
        return ParamDefinitions.of(
                ParamDefinition.enumOf(P_REQUIRE_PRICE, MODULE, "订单", "下单必须有有效采购价格", "WARN",
                        List.of(new ParamDefinition.Option("NONE", "不提示"), new ParamDefinition.Option("WARN", "提示"),
                                new ParamDefinition.Option("BLOCK", "阻止提交")), "没有有效价格时的处理").sort(10),
                ParamDefinition.decimal(P_PRICE_OVERRUN_PCT, MODULE, "订单", "单价超出价格表的容差（%）", "0", "0", "100",
                        "超出时订单行标记“超价”，可在审批流条件中使用").sort(20),
                ParamDefinition.bool(P_REQUIRE_SUPPLIER_MATERIAL, MODULE, "订单", "物料必须在供应商的合格可供物料中", true,
                        "样品订单允许试用状态的物料").sort(30),
                ParamDefinition.decimal(P_OVER_RECEIVE_PCT, MODULE, "到货", "默认超收比例（%）", "0", "0", "100",
                        "物料未设置超收比例时使用，如 5 表示可多收 5%").sort(10),
                ParamDefinition.enumOf(P_MIN_LIFE_CHECK, MODULE, "到货", "剩余保质期不足时", "WARN",
                        List.of(new ParamDefinition.Option("WARN", "提示"), new ParamDefinition.Option("BLOCK", "阻止")),
                        "剩余保质期比例低于物料的最小剩余比例时的处理").sort(20),
                ParamDefinition.integer(P_ONTIME_TOLERANCE, MODULE, "评估", "准时交货容差（天）", 0, 0, 30,
                        "到货日期 ≤ 确认交期 + N 天视为准时").sort(10),
                ParamDefinition.string(P_SCORE_WEIGHTS, MODULE, "评估", "评估权重", "40,30,20,10",
                        "质量、交期、价格、服务，逗号分隔，合计 100").sort(20),
                ParamDefinition.bool(P_SCORE_AUTO, MODULE, "评估", "每月 3 日自动计算上月评估", true, "关闭后只能手工计算").sort(30),
                ParamDefinition.integer(P_OVERDUE_REMIND_DAYS, MODULE, "跟单", "交期前提醒天数", 3, 0, 60,
                        "确认交期前 N 天未到货提醒采购员").sort(10),
                ParamDefinition.bool(P_MRP_AUTO_SUBMIT, MODULE, "申请", "MRP 生成的采购申请自动提交", false, "").sort(10),
                ParamDefinition.bool(P_PRICE_OTHER_MATERIAL, MODULE, "价格", "调价单允许选择非可供物料", false,
                        "允许时选择的物料自动加入该供应商的可供物料（试用）").sort(10),
                ParamDefinition.decimal(P_OVER_ISSUE_PCT, MODULE, "委外", "委外允许超发比例（%）", "0", "0", "100", "").sort(10));
    }

    // ==================== 审批 ====================

    @Bean
    public ApprovalBizDefinition purSupplierQualifyApproval() {
        return ApprovalBizDefinition.of(SUPPLIER_QUALIFY, "供应商准入", MODULE, "/purchase/supplier/{id}")
                .dictField("supplierType", "供应商类型", "pur_supplier_type")
                .userField("buyerId", "负责采购员");
    }

    @Bean
    public ApprovalBizDefinition purPriceAdjustApproval() {
        return ApprovalBizDefinition.of(PRICE_ADJUST, "调价单", MODULE, "/purchase/price-adjust/{id}")
                .numberField("maxIncreasePct", "最大涨幅(%)")
                .numberField("amountImpactBase", "金额影响(本位币)");
    }

    @Bean
    public ApprovalBizDefinition purRequisitionApproval() {
        return ApprovalBizDefinition.of(REQUISITION, "采购申请", MODULE, "/purchase/requisition/{id}")
                .dictField("requisitionType", "申请类型", "pur_requisition_type")
                .numberField("amountBase", "估算金额(本位币)");
    }

    @Bean
    public ApprovalBizDefinition purOrderApproval() {
        return ApprovalBizDefinition.of(ORDER, "采购订单", MODULE, "/purchase/order/{id}")
                .numberField("amountBase", "订单金额(本位币)")
                .dictField("supplierLevel", "供应商等级", "pur_supplier_level")
                .boolField("hasPriceOverrun", "有超价格表的行")
                .userField("buyerId", "采购员");
    }

    @Bean
    public ApprovalBizDefinition purOrderChangeApproval() {
        return ApprovalBizDefinition.of(ORDER_CHANGE, "采购订单变更", MODULE, "/purchase/order-change/{id}")
                .numberField("amountChangeBase", "金额变化(本位币)")
                .userField("buyerId", "采购员");
    }

    @Bean
    public ApprovalBizDefinition purReturnApproval() {
        return ApprovalBizDefinition.of(RETURN, "采购退货", MODULE, "/purchase/return/{id}")
                .numberField("amountBase", "退货金额(本位币)")
                .dictField("returnReason", "退货原因", "pur_return_reason");
    }

    @Bean
    public ApprovalBizDefinition purStatementApproval() {
        return ApprovalBizDefinition.of(STATEMENT, "供应商对账单", MODULE, "/purchase/statement/{id}")
                .numberField("amountBase", "对账金额(本位币)");
    }

    // ==================== 打印 ====================

    @Bean
    public PrintBizDefinition purOrderPrint() {
        return PrintBizDefinition.of(ORDER, "采购订单", MODULE, "/purchase/orders/{id}/print-data")
                .variable("docNo", "单号", "string").variable("docDate", "日期", "date").variable("status", "状态", "string")
                .variable("orderTypeName", "订单类型", "string").variable("orderVersion", "版本", "number")
                .variable("companyName", "本公司", "string").variable("supplierName", "供应商", "string").variable("supplierNameEn", "供应商英文名", "string")
                .variable("contactName", "联系人", "string").variable("contactPhone", "联系电话", "string")
                .variable("currency", "币别", "string").variable("paymentTermName", "付款条件", "string").variable("tradeTerm", "贸易条款", "string")
                .variable("deliveryAddress", "送货地址", "string").variable("buyerName", "采购员", "string").variable("remark", "备注", "string")
                .variable("amount", "不含税金额", "amount").variable("taxAmount", "税额", "amount").variable("totalAmount", "价税合计", "amount")
                .variable("lines", "明细", "array").variable("lines.lineNo", "行号", "number").variable("lines.materialCode", "物料编码", "string")
                .variable("lines.materialName", "名称", "string").variable("lines.spec", "规格", "string").variable("lines.supplierPartNo", "供应商料号", "string")
                .variable("lines.uom", "单位", "string").variable("lines.qty", "数量", "qty").variable("lines.priceInclTax", "含税单价", "price")
                .variable("lines.taxRate", "税率", "string").variable("lines.totalAmount", "价税合计", "amount")
                .variable("lines.requiredDate", "交期", "date").variable("lines.remark", "备注", "string")
                .sampleData("{\"docNo\":\"PO-20260924-0001\",\"docDate\":\"2026-09-24\",\"supplierName\":\"深圳华强电子\",\"currency\":\"CNY\","
                        + "\"totalAmount\":1130,\"lines\":[{\"lineNo\":1,\"materialCode\":\"ELEC00001\",\"materialName\":\"电阻\",\"uom\":\"PCS\","
                        + "\"qty\":1000,\"priceInclTax\":1.13,\"taxRate\":\"13%\",\"totalAmount\":1130,\"requiredDate\":\"2026-10-10\"}]}");
    }

    @Bean
    public PrintBizDefinition purReceiptPrint() {
        return PrintBizDefinition.of(RECEIPT, "到货单", MODULE, "/purchase/receipts/{id}/print-data")
                .variable("docNo", "单号", "string").variable("typeName", "到货类型", "string").variable("supplierName", "供应商", "string")
                .variable("deliveryNoteNo", "送货单号", "string").variable("arrivalAt", "到货时间", "datetime").variable("receiverName", "收货人", "string")
                .variable("remark", "备注", "string").variable("lines", "明细", "array").variable("lines.lineNo", "行号", "number")
                .variable("lines.orderNo", "订单号", "string").variable("lines.materialCode", "物料编码", "string").variable("lines.materialName", "名称", "string")
                .variable("lines.spec", "规格", "string").variable("lines.uom", "单位", "string").variable("lines.qty", "到货数量", "qty")
                .variable("lines.supplierBatchNo", "供应商批号", "string").variable("lines.inspect", "需检", "string")
                .sampleData("{\"docNo\":\"RC-20260924-0001\",\"supplierName\":\"深圳华强电子\",\"lines\":[{\"lineNo\":1,\"orderNo\":\"PO-20260920-0001\","
                        + "\"materialCode\":\"ELEC00001\",\"materialName\":\"电阻\",\"uom\":\"PCS\",\"qty\":1000,\"inspect\":\"是\"}]}");
    }

    @Bean
    public PrintBizDefinition purReturnPrint() {
        return PrintBizDefinition.of(RETURN, "采购退货单", MODULE, "/purchase/returns/{id}/print-data")
                .variable("docNo", "单号", "string").variable("docDate", "日期", "date").variable("supplierName", "供应商", "string")
                .variable("reasonName", "退货原因", "string").variable("handlingName", "处理方式", "string").variable("warehouseName", "出库仓", "string")
                .variable("currency", "币别", "string").variable("totalAmount", "退货金额", "amount").variable("remark", "备注", "string")
                .variable("lines", "明细", "array").variable("lines.lineNo", "行号", "number").variable("lines.receiptNo", "到货单号", "string")
                .variable("lines.materialCode", "物料编码", "string").variable("lines.materialName", "名称", "string").variable("lines.batchNo", "批次", "string")
                .variable("lines.qty", "退货数量", "qty").variable("lines.priceInclTax", "单价", "price").variable("lines.totalAmount", "金额", "amount")
                .sampleData("{\"docNo\":\"RT-202609-0001\",\"supplierName\":\"深圳华强电子\",\"lines\":[{\"lineNo\":1,\"materialCode\":\"ELEC00001\",\"qty\":20}]}");
    }

    @Bean
    public PrintBizDefinition purStatementPrint() {
        return PrintBizDefinition.of(STATEMENT, "供应商对账单", MODULE, "/purchase/statements/{id}/print-data")
                .variable("docNo", "单号", "string").variable("supplierName", "供应商", "string").variable("periodFrom", "区间开始", "date")
                .variable("periodTo", "区间结束", "date").variable("currency", "币别", "string").variable("goodsAmount", "货款", "amount")
                .variable("returnAmount", "退货", "amount").variable("adjustAmount", "调整", "amount").variable("totalAmount", "对账总额", "amount")
                .variable("lines", "明细", "array").variable("lines.lineTypeName", "类型", "string").variable("lines.sourceNo", "来源单号", "string")
                .variable("lines.orderNo", "订单号", "string").variable("lines.materialCode", "物料编码", "string").variable("lines.materialName", "名称", "string")
                .variable("lines.bizDate", "日期", "date").variable("lines.qty", "数量", "qty").variable("lines.priceInclTax", "含税单价", "price")
                .variable("lines.totalAmount", "金额", "amount").variable("lines.remark", "说明", "string")
                .sampleData("{\"docNo\":\"ST-202609-0001\",\"supplierName\":\"深圳华强电子\",\"currency\":\"CNY\",\"totalAmount\":1000,\"lines\":[]}");
    }

    @Bean
    public PrintBizDefinition purOutsourcingPrint() {
        return PrintBizDefinition.of(OUTSOURCING, "委外加工单", MODULE, "/purchase/outsourcings/{id}/print-data")
                .variable("docNo", "单号", "string").variable("docDate", "日期", "date").variable("supplierName", "加工商", "string")
                .variable("materialCode", "加工物料", "string").variable("materialName", "名称", "string").variable("qty", "数量", "qty")
                .variable("uom", "单位", "string").variable("processPrice", "加工费单价", "price").variable("totalAmount", "加工费合计", "amount")
                .variable("requiredDate", "要求交回日期", "date").variable("remark", "备注", "string")
                .variable("materials", "用料", "array").variable("materials.materialCode", "子件", "string").variable("materials.materialName", "名称", "string")
                .variable("materials.qtyPer", "单位用量", "qty").variable("materials.requiredQty", "应发数量", "qty").variable("materials.uom", "单位", "string")
                .sampleData("{\"docNo\":\"OS-202609-0001\",\"supplierName\":\"东莞加工厂\",\"materialCode\":\"SF0001\",\"qty\":100,\"materials\":[]}");
    }
}
