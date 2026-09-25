package com.erp.module.sales.config;

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

/** 销售模块的声明式注册（需求 04-销售 README 第 6～10、12 节） */
@Configuration
public class SalesModuleConfig {

    public static final String MODULE = "sales";

    // 编码规则 / 单据类型（审批、打印、附件、操作日志共用）
    public static final String PRICE_LIST = "SAL_PRICE_LIST";
    public static final String RFQ = "SAL_RFQ";
    public static final String QUOTATION = "SAL_QUOTATION";
    public static final String ORDER = "SAL_ORDER";
    public static final String ORDER_CHANGE = "SAL_ORDER_CHANGE";
    public static final String FORECAST = "SAL_FORECAST";
    public static final String RETURN = "SAL_RETURN";

    // 系统参数
    public static final String P_MIN_MARGIN = "sal.price.min-margin-pct";
    public static final String P_NO_COST_POLICY = "sal.price.no-cost-policy";
    public static final String P_QUOTE_VALID_DAYS = "sal.quotation.valid-days";
    public static final String P_MOQ_CHECK = "sal.order.moq-check";
    public static final String P_OVER_SHIP_PCT = "sal.order.over-ship-pct";
    public static final String P_AUTO_COMPLETE = "sal.order.auto-complete";
    public static final String P_CONSUME_WINDOW = "sal.forecast.consume-window";
    public static final String P_DELIVERY_WARN_DAYS = "sal.order.delivery-warn-days";
    public static final String P_PI_BANK_INFO = "sal.print.bank-info";
    public static final String P_DEFAULT_TAX_RATE = "sal.price.default-tax-rate";

    /** 字段权限：订单、价格表的成本与毛利 */
    public static final String ORDER_COST = "sales:order:cost";
    /** 字段权限：报价单、RFQ 的成本分析 */
    public static final String QUOTATION_COST = "sales:quotation:cost";

    @Bean
    public ErpModule salesModule() {
        return new ErpModule(MODULE, "销售", 210);
    }

    // ==================== 编码规则 ====================

    @Bean
    public CodeRuleDefinition salPriceListCodeRule() {
        return CodeRuleDefinition.of(PRICE_LIST, "销售价格表", MODULE, "PL-", "yyyy", "-", 3, ResetCycle.YEAR);
    }

    @Bean
    public CodeRuleDefinition salRfqCodeRule() {
        return CodeRuleDefinition.of(RFQ, "客户询价", MODULE, "CRFQ-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition salQuotationCodeRule() {
        return CodeRuleDefinition.of(QUOTATION, "报价单", MODULE, "QT-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition salOrderCodeRule() {
        return CodeRuleDefinition.of(ORDER, "销售订单", MODULE, "SO-", "yyyyMM", "-", 4, ResetCycle.MONTH).manual(true);
    }

    @Bean
    public CodeRuleDefinition salOrderChangeCodeRule() {
        return CodeRuleDefinition.of(ORDER_CHANGE, "销售订单变更单", MODULE, "SC-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition salForecastCodeRule() {
        return CodeRuleDefinition.of(FORECAST, "销售预测", MODULE, "FC-", "yyyyMM", "-", 3, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition salReturnCodeRule() {
        return CodeRuleDefinition.of(RETURN, "销售退货单", MODULE, "SR-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    // ==================== 权限点 ====================

    @Bean
    public PermissionDefinition salRfqPermissions() {
        return PermissionDefinition.group(MODULE, "rfq", "RFQ", 10)
                .menu("sales:rfq:query", "查看")
                .button("sales:rfq:create", "新建")
                .button("sales:rfq:update", "编辑")
                .button("sales:rfq:delete", "删除")
                .button("sales:rfq:assign", "分派/评估")
                .button("sales:rfq:cost", "成本核算")
                .button("sales:rfq:close", "关闭");
    }

    @Bean
    public PermissionDefinition salQuotationPermissions() {
        return PermissionDefinition.group(MODULE, "quotation", "报价", 20)
                .menu("sales:quotation:query", "查看")
                .button("sales:quotation:create", "新建")
                .button("sales:quotation:update", "编辑")
                .button("sales:quotation:delete", "删除")
                .button("sales:quotation:submit", "提交")
                .button("sales:quotation:revise", "修订")
                .button("sales:quotation:send", "发送")
                .button("sales:quotation:to-order", "转订单")
                .button("sales:quotation:lose", "未成交")
                .button("sales:quotation:print", "打印")
                .field(QUOTATION_COST, "查看成本与毛利（报价、RFQ）");
    }

    @Bean
    public PermissionDefinition salOrderPermissions() {
        return PermissionDefinition.group(MODULE, "order", "销售订单", 30)
                .menu("sales:order:query", "查看")
                .button("sales:order:create", "新建")
                .button("sales:order:update", "编辑")
                .button("sales:order:delete", "删除")
                .button("sales:order:submit", "提交")
                .button("sales:order:unapprove", "反审核")
                .button("sales:order:close", "关闭")
                .button("sales:order:void", "作废")
                .button("sales:order:change", "变更")
                .button("sales:order:print", "打印")
                .button("sales:order:export", "导出")
                .field(ORDER_COST, "查看成本与毛利（订单、价格表）");
    }

    @Bean
    public PermissionDefinition salForecastPermissions() {
        return PermissionDefinition.group(MODULE, "forecast", "销售预测", 40)
                .menu("sales:forecast:query", "查看")
                .button("sales:forecast:create", "新建")
                .button("sales:forecast:update", "编辑")
                .button("sales:forecast:delete", "删除")
                .button("sales:forecast:publish", "发布/关闭");
    }

    @Bean
    public PermissionDefinition salReturnPermissions() {
        return PermissionDefinition.group(MODULE, "return", "销售退货", 50)
                .menu("sales:return:query", "查看")
                .button("sales:return:create", "新建")
                .button("sales:return:update", "编辑")
                .button("sales:return:delete", "删除")
                .button("sales:return:submit", "提交")
                .button("sales:return:void", "作废")
                .button("sales:return:print", "打印");
    }

    @Bean
    public PermissionDefinition salPriceListPermissions() {
        return PermissionDefinition.group(MODULE, "price-list", "价格表", 60)
                .menu("sales:price-list:query", "查看")
                .button("sales:price-list:create", "新建")
                .button("sales:price-list:update", "编辑/关闭")
                .button("sales:price-list:delete", "删除")
                .button("sales:price-list:submit", "提交")
                .button("sales:price-list:import", "导入")
                .button("sales:price-list:export", "导出");
    }

    @Bean
    public PermissionDefinition salReportPermissions() {
        return PermissionDefinition.group(MODULE, "report", "销售报表", 70)
                .menu("sales:report:query", "查看")
                .button("sales:report:export", "导出");
    }

    // ==================== 字典 ====================

    @Bean
    public DictDefinition salOrderTypeDict() {
        return DictDefinition.of("sal_order_type", "订单类型", MODULE)
                .add(new DictDefinition.Item("NORMAL", "正常订单", "Normal", DictDefinition.TagType.PRIMARY, true, true))
                .add(new DictDefinition.Item("SAMPLE", "样品订单", "Sample", DictDefinition.TagType.INFO, true, false))
                .add(new DictDefinition.Item("REPLACEMENT", "补货订单", "Replacement", DictDefinition.TagType.WARNING, true, false))
                .add(new DictDefinition.Item("STOCK", "备货订单", "Stock", DictDefinition.TagType.SUCCESS, true, false));
    }

    @Bean
    public DictDefinition salQuoteLostReasonDict() {
        return DictDefinition.of("sal_quote_lost_reason", "报价失败原因", MODULE)
                .builtin("PRICE", "价格高", "Price").builtin("DELIVERY", "交期长", "Delivery").builtin("SPEC", "规格不符", "Spec")
                .builtin("NO_RESPONSE", "客户无回复", "No response").builtin("CANCELED", "项目取消", "Canceled").builtin("OTHER", "其他", "Other");
    }

    @Bean
    public DictDefinition salReturnReasonDict() {
        return DictDefinition.of("sal_return_reason", "退货原因", MODULE)
                .builtin("QUALITY", "质量问题", "Quality").builtin("WRONG_GOODS", "发错货", "Wrong goods")
                .builtin("DAMAGED", "运输损坏", "Damaged").builtin("CUSTOMER_CHANGE", "客户原因", "Customer change").builtin("OTHER", "其他", "Other");
    }

    @Bean
    public DictDefinition salChangeReasonDict() {
        return DictDefinition.of("sal_change_reason", "订单变更原因", MODULE)
                .builtin("CUSTOMER", "客户要求", "Customer").builtin("INTERNAL", "内部原因（产能/物料）", "Internal")
                .builtin("PRICE", "价格调整", "Price").builtin("OTHER", "其他", "Other");
    }

    // ==================== 系统参数 ====================

    @Bean
    public ParamDefinitions salesParams() {
        return ParamDefinitions.of(
                ParamDefinition.decimal(P_MIN_MARGIN, MODULE, "价格", "最低毛利率（%）", "15", "0", "100",
                        "底价 = 标准成本 × (1 + 最低毛利率)；低于底价标记“低于底价”，可在审批流条件中使用").sort(10),
                ParamDefinition.enumOf(P_NO_COST_POLICY, MODULE, "价格", "物料无标准成本时", "WARN",
                        List.of(new ParamDefinition.Option("IGNORE", "忽略"), new ParamDefinition.Option("WARN", "提示")),
                        "无法计算毛利时的处理").sort(20),
                ParamDefinition.decimal(P_DEFAULT_TAX_RATE, MODULE, "价格", "默认销项税率（%）", "13", "0", "100",
                        "客户未设置税率时使用；通用 / 等级价格表按此换算不含税价计算毛利").sort(30),
                ParamDefinition.integer(P_QUOTE_VALID_DAYS, MODULE, "报价", "默认报价有效期（天）", 30, 1, 365, "").sort(10),
                ParamDefinition.enumOf(P_MOQ_CHECK, MODULE, "订单", "MOQ 检查", "WARN",
                        List.of(new ParamDefinition.Option("NONE", "不检查"), new ParamDefinition.Option("WARN", "警告"),
                                new ParamDefinition.Option("BLOCK", "阻止")), "使用物料计划属性中的 MOQ").sort(10),
                ParamDefinition.decimal(P_OVER_SHIP_PCT, MODULE, "订单", "允许超出货比例（%）", "0", "0", "100",
                        "可通知数量 = 订单数量 × (1 + 比例) − 已通知").sort(20),
                ParamDefinition.bool(P_AUTO_COMPLETE, MODULE, "订单", "全部出货后自动完成", true, "否：需全部回款后才完成").sort(30),
                ParamDefinition.integer(P_DELIVERY_WARN_DAYS, MODULE, "订单", "交期预警天数", 3, 0, 60,
                        "承诺交期（无则要求交期）前 N 天仍未出货提醒业务员").sort(40),
                ParamDefinition.string(P_CONSUME_WINDOW, MODULE, "预测", "预测冲销窗口（前后月数）", "0,1",
                        "“向前 N 个月,向后 M 个月”，先冲要求交期所在月").sort(10),
                ParamDefinition.string(P_PI_BANK_INFO, MODULE, "打印", "收款银行信息", "",
                        "打印在 Proforma Invoice 上的银行名称、账户名、账号、SWIFT，多行用 ; 分隔").sort(10));
    }

    // ==================== 审批 ====================

    @Bean
    public ApprovalBizDefinition salQuotationApproval() {
        return ApprovalBizDefinition.of(QUOTATION, "报价单", MODULE, "/sales/quotation/{id}")
                .numberField("amountBase", "参考金额(本位币)")
                .numberField("minMarginRate", "最低毛利率(%)")
                .boolField("belowFloor", "低于底价")
                .dictField("customerLevel", "客户等级", "crm_customer_level")
                .userField("ownerId", "业务员");
    }

    @Bean
    public ApprovalBizDefinition salOrderApproval() {
        return ApprovalBizDefinition.of(ORDER, "销售订单", MODULE, "/sales/order/{id}")
                .numberField("amountBase", "价税合计(本位币)")
                .numberField("minMarginRate", "最低毛利率(%)")
                .boolField("belowFloor", "低于底价")
                .dictField("customerLevel", "客户等级", "crm_customer_level")
                .dictField("orderType", "订单类型", "sal_order_type")
                .boolField("creditWarning", "信用预警")
                .userField("ownerId", "业务员");
    }

    @Bean
    public ApprovalBizDefinition salOrderChangeApproval() {
        return ApprovalBizDefinition.of(ORDER_CHANGE, "订单变更", MODULE, "/sales/order-change/{id}")
                .numberField("amountChangeBase", "金额变化(本位币)")
                .dictField("changeReason", "变更原因", "sal_change_reason")
                .userField("ownerId", "业务员");
    }

    @Bean
    public ApprovalBizDefinition salReturnApproval() {
        return ApprovalBizDefinition.of(RETURN, "销售退货", MODULE, "/sales/return/{id}")
                .numberField("amountBase", "退货金额(本位币)")
                .dictField("returnReason", "退货原因", "sal_return_reason")
                .userField("ownerId", "业务员");
    }

    @Bean
    public ApprovalBizDefinition salPriceListApproval() {
        return ApprovalBizDefinition.of(PRICE_LIST, "销售价格表", MODULE, "/sales/price-list/{id}");
    }

    // ==================== 打印 ====================

    @Bean
    public PrintBizDefinition salQuotationPrint() {
        return PrintBizDefinition.of(QUOTATION, "报价单", MODULE, "/sales/quotations/{id}/print-data")
                .variable("docNo", "单号", "string").variable("revisionText", "版本", "string").variable("docDate", "日期", "date")
                .variable("validUntil", "有效期至", "date").variable("status", "状态", "string")
                .variable("companyName", "本公司", "string").variable("companyAddress", "本公司地址", "string")
                .variable("customerName", "客户", "string").variable("contactName", "联系人", "string")
                .variable("currency", "币别", "string").variable("tradeTerm", "贸易条款", "string").variable("paymentTermName", "付款条件", "string")
                .variable("taxIncludedText", "含税说明", "string").variable("terms", "报价条款", "string").variable("ownerName", "业务员", "string")
                .variable("lines", "明细", "array").variable("lines.lineNo", "行号", "number").variable("lines.materialCode", "物料编码", "string")
                .variable("lines.customerPartNo", "客户料号", "string").variable("lines.description", "描述", "string").variable("lines.uom", "单位", "string")
                .variable("lines.minQty", "起始数量", "qty").variable("lines.price", "单价", "price").variable("lines.moq", "MOQ", "qty")
                .variable("lines.leadTimeDays", "交期(天)", "number").variable("lines.toolingFee", "模具费", "amount").variable("lines.remark", "备注", "string")
                .sampleData("{\"docNo\":\"QT-202609-0001\",\"revisionText\":\"R0\",\"customerName\":\"ABC Trading\",\"currency\":\"USD\","
                        + "\"lines\":[{\"lineNo\":1,\"materialCode\":\"FG0001\",\"description\":\"FPC Assembly\",\"uom\":\"PCS\",\"minQty\":1000,\"price\":1.25}]}");
    }

    @Bean
    public PrintBizDefinition salOrderPrint() {
        return PrintBizDefinition.of(ORDER, "销售订单（合同 / PI）", MODULE, "/sales/orders/{id}/print-data")
                .variable("docNo", "单号", "string").variable("docDate", "日期", "date").variable("status", "状态", "string")
                .variable("orderVersion", "版本", "number").variable("companyName", "本公司", "string").variable("companyAddress", "本公司地址", "string")
                .variable("customerName", "客户", "string").variable("customerPoNo", "客户 PO 号", "string").variable("contactName", "联系人", "string")
                .variable("shipTo", "收货地址", "string").variable("currency", "币别", "string").variable("paymentTermName", "付款条件", "string")
                .variable("tradeTerm", "贸易条款", "string").variable("portOfLoading", "起运港", "string").variable("portOfDestination", "目的港", "string")
                .variable("amount", "不含税金额", "amount").variable("taxAmount", "税额", "amount").variable("totalAmount", "价税合计", "amount")
                .variable("bankInfo", "收款银行", "array").variable("terms", "合同条款", "string").variable("ownerName", "业务员", "string")
                .variable("lines", "明细", "array").variable("lines.lineNo", "行号", "number").variable("lines.materialCode", "物料编码", "string")
                .variable("lines.customerPartNo", "客户料号", "string").variable("lines.description", "描述", "string").variable("lines.uom", "单位", "string")
                .variable("lines.qty", "数量", "qty").variable("lines.price", "单价", "price").variable("lines.totalAmount", "金额", "amount")
                .variable("lines.deliveryDate", "交期", "date")
                .sampleData("{\"docNo\":\"SO-202609-0001\",\"customerName\":\"ABC Trading\",\"currency\":\"USD\",\"totalAmount\":1250,"
                        + "\"lines\":[{\"lineNo\":1,\"materialCode\":\"FG0001\",\"uom\":\"PCS\",\"qty\":1000,\"price\":1.25,\"totalAmount\":1250}]}");
    }

    @Bean
    public PrintBizDefinition salReturnPrint() {
        return PrintBizDefinition.of(RETURN, "销售退货单", MODULE, "/sales/returns/{id}/print-data")
                .variable("docNo", "单号", "string").variable("docDate", "日期", "date").variable("customerName", "客户", "string")
                .variable("rmaNo", "RMA 号", "string").variable("reasonName", "退货原因", "string").variable("handlingName", "处理方式", "string")
                .variable("currency", "币别", "string").variable("totalAmount", "退货金额", "amount").variable("remark", "问题描述", "string")
                .variable("lines", "明细", "array").variable("lines.lineNo", "行号", "number").variable("lines.orderNo", "订单号", "string")
                .variable("lines.materialCode", "物料编码", "string").variable("lines.materialName", "名称", "string").variable("lines.batchNo", "批次", "string")
                .variable("lines.qty", "退货数量", "qty").variable("lines.priceInclTax", "单价", "price").variable("lines.totalAmount", "金额", "amount")
                .sampleData("{\"docNo\":\"SR-202609-0001\",\"customerName\":\"ABC Trading\",\"lines\":[{\"lineNo\":1,\"materialCode\":\"FG0001\",\"qty\":50}]}");
    }
}
