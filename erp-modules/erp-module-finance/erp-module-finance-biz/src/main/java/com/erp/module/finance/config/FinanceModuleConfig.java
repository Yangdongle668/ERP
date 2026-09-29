package com.erp.module.finance.config;

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

/** 财务模块的声明式注册（需求 12-财务 README 第 6～10 节） */
@Configuration
public class FinanceModuleConfig {

    public static final String MODULE = "finance";

    // 编码规则 / 单据类型
    public static final String RECEIVABLE = "FIN_RECEIVABLE";
    public static final String OTHER_RECEIVABLE = "FIN_OTHER_RECEIVABLE";
    public static final String SALES_INVOICE = "FIN_SALES_INVOICE";
    public static final String RECEIPT = "FIN_RECEIPT";
    public static final String PAYABLE = "FIN_PAYABLE";
    public static final String OTHER_PAYABLE = "FIN_OTHER_PAYABLE";
    public static final String PURCHASE_INVOICE = "FIN_PURCHASE_INVOICE";
    public static final String PAYMENT_REQUEST = "FIN_PAYMENT_REQUEST";
    public static final String PAYMENT = "FIN_PAYMENT";
    public static final String VOUCHER = "FIN_VOUCHER";
    public static final String CUSTOMER_STATEMENT = "FIN_CUSTOMER_STATEMENT";

    // 系统参数
    public static final String P_AR_AUTO_CONFIRM = "fin.ar.auto-confirm";
    public static final String P_AP_AUTO_CONFIRM = "fin.ap.auto-confirm";
    public static final String P_PRICE_TOLERANCE = "fin.ap.invoice-price-tolerance";
    public static final String P_AMOUNT_TOLERANCE = "fin.ap.invoice-amount-tolerance";
    public static final String P_ALLOW_UNINVOICED = "fin.ap.allow-uninvoiced-request";
    public static final String P_ADVANCE_ANY_ORDER = "fin.ar.advance-any-order";
    public static final String P_VOUCHER_AUTO = "fin.voucher.auto-generate";
    public static final String P_LABOR_ALLOC = "fin.cost.labor-allocation";
    public static final String P_OVERHEAD_ALLOC = "fin.cost.overhead-allocation";
    public static final String P_WIP_METHOD = "fin.cost.wip-method";
    public static final String P_COMPANY_BANK = "fin.base.company-bank";
    public static final String P_FX_REVERSE = "fin.close.fx-auto-reverse";

    @Bean
    public ErpModule financeModule() {
        return new ErpModule(MODULE, "财务", 300);
    }

    // ==================== 编码规则 ====================

    @Bean
    public CodeRuleDefinition finReceivableCodeRule() {
        return CodeRuleDefinition.of(RECEIVABLE, "应收单", MODULE, "AR-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition finSalesInvoiceCodeRule() {
        return CodeRuleDefinition.of(SALES_INVOICE, "销项发票登记", MODULE, "SI-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition finReceiptCodeRule() {
        return CodeRuleDefinition.of(RECEIPT, "收款单", MODULE, "RV-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition finPayableCodeRule() {
        return CodeRuleDefinition.of(PAYABLE, "应付单", MODULE, "AP-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition finPurchaseInvoiceCodeRule() {
        return CodeRuleDefinition.of(PURCHASE_INVOICE, "进项发票登记", MODULE, "PI-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition finPaymentRequestCodeRule() {
        return CodeRuleDefinition.of(PAYMENT_REQUEST, "付款申请", MODULE, "PQ-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition finPaymentCodeRule() {
        return CodeRuleDefinition.of(PAYMENT, "付款单", MODULE, "PY-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    // ==================== 权限点 ====================

    @Bean
    public PermissionDefinition finSettingPermissions() {
        return PermissionDefinition.group(MODULE, "setting", "财务设置", 90)
                .menu("fin:setting:query", "查看")
                .button("fin:account:manage", "会计科目")
                .button("fin:period:manage", "会计期间")
                .button("fin:bank:manage", "银行账户")
                .button("fin:mapping:manage", "科目映射");
    }

    @Bean
    public PermissionDefinition finReceivablePermissions() {
        return PermissionDefinition.group(MODULE, "receivable", "应收", 10)
                .menu("fin:receivable:query", "查看")
                .button("fin:receivable:confirm", "确认")
                .button("fin:receivable:unconfirm", "反确认 / 作废")
                .button("fin:receivable:create-other", "新建其他应收")
                .button("fin:receivable:invoice", "开票登记")
                .button("fin:receivable:export", "导出");
    }

    @Bean
    public PermissionDefinition finReceiptPermissions() {
        return PermissionDefinition.group(MODULE, "receipt", "收款", 20)
                .menu("fin:receipt:query", "查看")
                .button("fin:receipt:create", "新建 / 导入")
                .button("fin:receipt:update", "编辑")
                .button("fin:receipt:delete", "删除 / 作废")
                .button("fin:receipt:confirm", "确认")
                .button("fin:receipt:unconfirm", "反确认")
                .button("fin:receipt:verify", "核销")
                .button("fin:receipt:unverify", "反核销");
    }

    @Bean
    public PermissionDefinition finPayablePermissions() {
        return PermissionDefinition.group(MODULE, "payable", "应付", 30)
                .menu("fin:payable:query", "查看")
                .button("fin:payable:confirm", "确认")
                .button("fin:payable:unconfirm", "反确认 / 作废")
                .button("fin:payable:create-other", "新建其他应付")
                .button("fin:payable:invoice", "登记发票 / 确认差异")
                .button("fin:payable:export", "导出");
    }

    @Bean
    public PermissionDefinition finPaymentPermissions() {
        return PermissionDefinition.group(MODULE, "payment", "付款", 40)
                .menu("fin:payment:query", "付款单")
                .button("fin:payment:create", "新建付款")
                .button("fin:payment:confirm", "确认付款 / 反确认")
                .button("fin:payment:verify", "预付冲应付 / 反核销")
                .menu("fin:payment-request:query", "付款申请")
                .button("fin:payment-request:create", "新建 / 编辑申请")
                .button("fin:payment-request:submit", "提交 / 关闭申请");
    }

    @Bean
    public PermissionDefinition finVoucherPermissions() {
        return PermissionDefinition.group(MODULE, "voucher", "凭证", 50)
                .menu("fin:voucher:query", "查看")
                .button("fin:voucher:create", "新建 / 生成")
                .button("fin:voucher:update", "编辑 / 删除")
                .button("fin:voucher:audit", "审核 / 反审核")
                .button("fin:voucher:post", "过账")
                .button("fin:voucher:unpost", "反过账")
                .button("fin:voucher:export", "导出");
    }

    @Bean
    public PermissionDefinition finCostPermissions() {
        return PermissionDefinition.group(MODULE, "cost", "成本", 60)
                .menu("fin:cost:query", "查看")
                .button("fin:cost:calculate", "费用录入 / 计算")
                .button("fin:cost:lock", "锁定 / 解锁");
    }

    @Bean
    public PermissionDefinition finReportPermissions() {
        return PermissionDefinition.group(MODULE, "report", "财务报表", 70)
                .menu("fin:report:query", "查看")
                .button("fin:report:export", "导出");
    }

    @Bean
    public PermissionDefinition finClosePermissions() {
        return PermissionDefinition.group(MODULE, "close", "月结", 80)
                .menu("fin:close:query", "查看")
                .button("fin:close:execute", "结账")
                .button("fin:close:reopen", "反结账");
    }

    // ==================== 字典 ====================

    @Bean
    public DictDefinition finInvoiceTypeDict() {
        return DictDefinition.of("fin_invoice_type", "发票类型", MODULE)
                .builtin("VAT_SPECIAL", "增值税专用发票", "VAT Special").builtin("VAT_NORMAL", "增值税普通发票", "VAT Normal")
                .builtin("EXPORT", "出口发票", "Export").builtin("OTHER", "其他", "Other");
    }

    // ==================== 系统参数 ====================

    @Bean
    public ParamDefinitions finParams() {
        return ParamDefinitions.of(
                ParamDefinition.bool(P_AR_AUTO_CONFIRM, MODULE, "应收", "应收单自动确认", true, "否：应收会计逐张确认").sort(10),
                ParamDefinition.bool(P_ADVANCE_ANY_ORDER, MODULE, "应收", "预收款可核销其他订单的应收", false, "否：预收只能冲对应订单产生的应收").sort(20),
                ParamDefinition.bool(P_AP_AUTO_CONFIRM, MODULE, "应付", "应付单自动确认", false, "对账单确认后生成的应付单直接确认").sort(10),
                ParamDefinition.decimal(P_PRICE_TOLERANCE, MODULE, "应付", "发票单价容差（%）", "1", "0", "100", "三单匹配单价差异容差").sort(20),
                ParamDefinition.decimal(P_AMOUNT_TOLERANCE, MODULE, "应付", "发票金额尾差容差（元）", "1", "0", "1000", "明细合计与发票金额的尾差自动调整到最后一行").sort(30),
                ParamDefinition.bool(P_ALLOW_UNINVOICED, MODULE, "应付", "未收到发票的应付可申请付款", true, "是：提示“该应付尚未收到发票”；否：阻止").sort(40),
                ParamDefinition.bool(P_VOUCHER_AUTO, MODULE, "凭证", "业务单据确认后自动生成凭证草稿", false, "否：月末批量生成").sort(10),
                ParamDefinition.enumOf(P_LABOR_ALLOC, MODULE, "成本", "人工费用分配依据", "WORK_HOURS",
                        List.of(new ParamDefinition.Option("WORK_HOURS", "实际工时"), new ParamDefinition.Option("STD_HOURS", "标准工时"),
                                new ParamDefinition.Option("OUTPUT", "产量")), "").sort(10),
                ParamDefinition.enumOf(P_OVERHEAD_ALLOC, MODULE, "成本", "制造费用分配依据", "WORK_HOURS",
                        List.of(new ParamDefinition.Option("WORK_HOURS", "实际工时"), new ParamDefinition.Option("STD_HOURS", "标准工时"),
                                new ParamDefinition.Option("OUTPUT", "产量"), new ParamDefinition.Option("MATERIAL", "材料成本")), "").sort(20),
                ParamDefinition.enumOf(P_WIP_METHOD, MODULE, "成本", "在制品计价", "MATERIAL_ONLY",
                        List.of(new ParamDefinition.Option("MATERIAL_ONLY", "在制品只计材料"), new ParamDefinition.Option("EQUIVALENT", "约当产量法")), "").sort(30),
                ParamDefinition.string(P_COMPANY_BANK, MODULE, "单证", "Invoice 默认收款账户", "", "银行账户编码").sort(10),
                ParamDefinition.bool(P_FX_REVERSE, MODULE, "月结", "外币重估下月初自动冲回", true, "").sort(10));
    }

    // ==================== 审批 ====================

    @Bean
    public ApprovalBizDefinition finOtherReceivableApproval() {
        return ApprovalBizDefinition.of(OTHER_RECEIVABLE, "其他应收", MODULE, "/finance/receivable/{id}").numberField("amountBase", "金额(本位币)");
    }

    @Bean
    public ApprovalBizDefinition finOtherPayableApproval() {
        return ApprovalBizDefinition.of(OTHER_PAYABLE, "其他应付", MODULE, "/finance/payable/{id}").numberField("amountBase", "金额(本位币)");
    }

    @Bean
    public ApprovalBizDefinition finPaymentRequestApproval() {
        return ApprovalBizDefinition.of(PAYMENT_REQUEST, "付款申请", MODULE, "/finance/payment-request/{id}")
                .numberField("amountBase", "金额(本位币)")
                .boolField("hasPrepayment", "含预付款")
                .dictField("supplierLevel", "供应商等级", "pur_supplier_level")
                .userField("ownerId", "申请人");
    }

    // ==================== 打印 ====================

    @Bean
    public PrintBizDefinition finPaymentRequestPrint() {
        return PrintBizDefinition.of(PAYMENT_REQUEST, "付款申请单", MODULE, "/finance/payment-requests/{id}/print-data")
                .variable("docNo", "单号", "string").variable("supplierName", "供应商", "string").variable("requestTypeName", "类型", "string")
                .variable("currency", "币别", "string").variable("amount", "申请金额", "amount").variable("amountInWords", "大写金额", "string")
                .variable("planPayDate", "计划付款日", "date").variable("bankText", "收款账户", "string").variable("reason", "原因", "string")
                .variable("ownerName", "申请人", "string")
                .variable("lines", "应付明细", "array").variable("lines.payableNo", "应付单", "string").variable("lines.dueDate", "到期日", "date")
                .variable("lines.amount", "申请金额", "amount")
                .sampleData("{\"docNo\":\"PQ-202609-0001\",\"supplierName\":\"深圳某电子\",\"currency\":\"CNY\",\"amount\":10735,\"lines\":[{\"payableNo\":\"AP-202609-0001\",\"amount\":10735}]}");
    }

    @Bean
    public PrintBizDefinition finCustomerStatementPrint() {
        return PrintBizDefinition.of(CUSTOMER_STATEMENT, "客户对账单", MODULE, "/finance/reports/customer-statement/print-data?customerId={id}")
                .variable("customerName", "客户", "string").variable("currency", "币别", "string").variable("dateFrom", "期间起", "date")
                .variable("dateTo", "期间止", "date").variable("opening", "期初余额", "amount").variable("closing", "期末余额", "amount")
                .variable("lines", "明细", "array").variable("lines.date", "日期", "date").variable("lines.docNo", "单号", "string")
                .variable("lines.summary", "摘要", "string").variable("lines.debit", "应收", "amount").variable("lines.credit", "收款", "amount")
                .variable("lines.balance", "余额", "amount")
                .sampleData("{\"customerName\":\"ABC Ltd\",\"currency\":\"USD\",\"opening\":2000,\"closing\":6000,\"lines\":[{\"docNo\":\"AR-202609-0001\",\"debit\":10000}]}");
    }
}
