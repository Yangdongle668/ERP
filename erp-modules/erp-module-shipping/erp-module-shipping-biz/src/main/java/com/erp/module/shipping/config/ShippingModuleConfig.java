package com.erp.module.shipping.config;

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

/** 出货模块的声明式注册（需求 11-出货 README 第 6～12 节） */
@Configuration
public class ShippingModuleConfig {

    public static final String MODULE = "shipping";

    // 编码规则 / 单据类型
    public static final String NOTICE = "SHP_NOTICE";
    public static final String PICKING = "SHP_PICKING";
    public static final String SHIPMENT = "SHP_SHIPMENT";
    public static final String PACKING_LIST = "SHP_PACKING_LIST";
    public static final String INVOICE = "SHP_INVOICE";
    public static final String CUSTOMS = "SHP_CUSTOMS";
    public static final String CARTON_LABEL = "SHP_CARTON_LABEL";

    // 系统参数
    public static final String P_CREDIT_CHECK = "shp.notice.credit-check";
    public static final String P_PREPAYMENT = "shp.shipment.prepayment-check";
    public static final String P_OQC_DEFAULT = "shp.oqc.required-default";
    public static final String P_PICKING = "shp.picking.enabled";
    public static final String P_PACKING = "shp.packing.enabled";
    public static final String P_PENDING_DAYS = "shp.report.pending-days";

    @Bean
    public ErpModule shippingModule() {
        return new ErpModule(MODULE, "出货", 260);
    }

    // ==================== 编码规则 ====================

    @Bean
    public CodeRuleDefinition shpNoticeCodeRule() {
        return CodeRuleDefinition.of(NOTICE, "出货通知", MODULE, "SN-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition shpPickingCodeRule() {
        return CodeRuleDefinition.of(PICKING, "拣货单", MODULE, "PK-", "yyyyMMdd", "-", 3, ResetCycle.DAY);
    }

    @Bean
    public CodeRuleDefinition shpShipmentCodeRule() {
        return CodeRuleDefinition.of(SHIPMENT, "出货单", MODULE, "SH-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition shpPackingListCodeRule() {
        return CodeRuleDefinition.of(PACKING_LIST, "Packing List", MODULE, "PL", "yyyyMM", "", 4, ResetCycle.MONTH).manual(true);
    }

    @Bean
    public CodeRuleDefinition shpInvoiceCodeRule() {
        return CodeRuleDefinition.of(INVOICE, "Commercial Invoice", MODULE, "CI", "yyyyMM", "", 4, ResetCycle.MONTH).manual(true);
    }

    @Bean
    public CodeRuleDefinition shpCustomsCodeRule() {
        return CodeRuleDefinition.of(CUSTOMS, "报关资料", MODULE, "CD-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    // ==================== 权限点 ====================

    @Bean
    public PermissionDefinition shpNoticePermissions() {
        return PermissionDefinition.group(MODULE, "notice", "出货通知", 10)
                .menu("shp:notice:query", "查看")
                .button("shp:notice:create", "新建")
                .button("shp:notice:update", "编辑")
                .button("shp:notice:delete", "删除")
                .button("shp:notice:submit", "提交")
                .button("shp:notice:unapprove", "反审核")
                .button("shp:notice:close", "关闭")
                .button("shp:notice:print", "打印");
    }

    @Bean
    public PermissionDefinition shpPickingPermissions() {
        return PermissionDefinition.group(MODULE, "picking", "拣货", 20)
                .menu("shp:picking:query", "查看")
                .button("shp:picking:pick", "录入拣货")
                .button("shp:picking:print", "打印");
    }

    @Bean
    public PermissionDefinition shpPackingPermissions() {
        return PermissionDefinition.group(MODULE, "packing", "装箱", 30)
                .menu("shp:packing:query", "查看")
                .button("shp:packing:pack", "装箱 / 申请 OQC")
                .button("shp:packing:print-label", "打印箱唛");
    }

    @Bean
    public PermissionDefinition shpShipmentPermissions() {
        return PermissionDefinition.group(MODULE, "shipment", "出货单", 40)
                .menu("shp:shipment:query", "查看")
                .button("shp:shipment:create", "生成")
                .button("shp:shipment:update", "编辑")
                .button("shp:shipment:delete", "删除 / 作废")
                .button("shp:shipment:submit", "提交")
                .button("shp:shipment:withdraw", "撤回")
                .button("shp:shipment:print", "打印")
                .button("shp:shipment:export", "导出");
    }

    @Bean
    public PermissionDefinition shpDocumentPermissions() {
        return PermissionDefinition.group(MODULE, "document", "出货单证", 50)
                .menu("shp:document:query", "查看")
                .button("shp:document:create", "生成")
                .button("shp:document:update", "编辑")
                .button("shp:document:print", "打印 / 导出")
                .button("shp:document:price", "查看 Invoice 价格");
    }

    @Bean
    public PermissionDefinition shpLogisticsPermissions() {
        return PermissionDefinition.group(MODULE, "logistics", "物流", 60)
                .menu("shp:logistics:query", "物流跟踪")
                .button("shp:logistics:update", "登记物流 / 签收")
                .menu("shp:forwarder:manage", "货代");
    }

    @Bean
    public PermissionDefinition shpReportPermissions() {
        return PermissionDefinition.group(MODULE, "report", "出货报表", 70)
                .menu("shp:report:query", "查看")
                .button("shp:report:export", "导出");
    }

    // ==================== 字典 ====================

    @Bean
    public DictDefinition shpTransportModeDict() {
        return DictDefinition.of("shp_transport_mode", "运输方式", MODULE)
                .builtin("SEA", "海运", "Sea").builtin("AIR", "空运", "Air").builtin("EXPRESS", "快递", "Express").builtin("LAND", "陆运", "Land")
                .builtin("RAIL", "铁路", "Rail");
    }

    @Bean
    public DictDefinition shpContainerTypeDict() {
        return DictDefinition.of("shp_container_type", "柜型", MODULE)
                .builtin("20GP", "20GP", "20GP").builtin("40GP", "40GP", "40GP").builtin("40HQ", "40HQ", "40HQ").builtin("LCL", "拼箱", "LCL");
    }

    @Bean
    public DictDefinition shpCartonSpecDict() {
        return DictDefinition.of("shp_carton_spec", "常用外箱规格", MODULE);
    }

    @Bean
    public DictDefinition shpLogisticsStatusDict() {
        return DictDefinition.of("shp_logistics_status", "物流状态", MODULE)
                .builtin("BOOKED", "已订舱", "Booked").builtin("LOADED", "已装柜", "Loaded").builtin("DEPARTED", "已离港", "Departed")
                .builtin("ARRIVED", "已到港", "Arrived").builtin("CLEARED", "已清关", "Cleared").builtin("DELIVERED", "已签收", "Delivered");
    }

    // ==================== 系统参数 ====================

    @Bean
    public ParamDefinitions shpParams() {
        return ParamDefinitions.of(
                ParamDefinition.bool(P_CREDIT_CHECK, MODULE, "出货", "出货通知提交时检查信用", true, "使用 CRM 信用规则").sort(10),
                ParamDefinition.enumOf(P_PREPAYMENT, MODULE, "出货", "出货前款项未收齐时", "WARN",
                        List.of(new ParamDefinition.Option("NONE", "不检查"), new ParamDefinition.Option("WARN", "警告"), new ParamDefinition.Option("BLOCK", "阻止")),
                        "检查销售回款计划中“出货前”节点").sort(20),
                ParamDefinition.integer(P_PENDING_DAYS, MODULE, "出货", "待出货清单天数", 14, 1, 90, "承诺交期在未来 N 天内尚未通知的订单行").sort(30),
                ParamDefinition.bool(P_OQC_DEFAULT, MODULE, "OQC", "未设置物料 OQC 属性时默认需要 OQC", false, "以物料质量属性“出货检验”为准").sort(10),
                ParamDefinition.bool(P_PICKING, MODULE, "拣货", "启用拣货单", true, "否：出货单直接生成出库单，由仓管员确认时分配批次").sort(10),
                ParamDefinition.bool(P_PACKING, MODULE, "装箱", "启用装箱", true, "否：Packing List 按出货单行简单生成（不记录箱号）").sort(10));
    }

    // ==================== 审批 ====================

    @Bean
    public ApprovalBizDefinition shpNoticeApproval() {
        return ApprovalBizDefinition.of(NOTICE, "出货通知", MODULE, "/shipping/notice/{id}")
                .numberField("amountBase", "金额(本位币)")
                .boolField("creditWarning", "信用预警")
                .boolField("prepaymentUnpaid", "出货前款项未收齐")
                .userField("ownerId", "船务");
    }

    @Bean
    public ApprovalBizDefinition shpShipmentApproval() {
        return ApprovalBizDefinition.of(SHIPMENT, "出货单（放行）", MODULE, "/shipping/shipment/{id}")
                .numberField("amountBase", "金额(本位币)")
                .boolField("creditWarning", "信用预警")
                .boolField("prepaymentUnpaid", "出货前款项未收齐")
                .userField("ownerId", "船务");
    }

    // ==================== 打印 ====================

    @Bean
    public PrintBizDefinition shpNoticePrint() {
        return PrintBizDefinition.of(NOTICE, "出货通知", MODULE, "/shipping/notices/{id}/print-data")
                .variable("docNo", "单号", "string").variable("status", "状态", "string").variable("customerName", "客户", "string")
                .variable("shipDate", "出货日期", "date").variable("transportModeName", "运输方式", "string").variable("portOfDestination", "目的港", "string")
                .variable("shipToText", "收货地址", "string").variable("warehouseName", "出货仓", "string").variable("remark", "备注", "string")
                .variable("lines", "明细", "array").variable("lines.lineNo", "行号", "number").variable("lines.orderNo", "订单号", "string")
                .variable("lines.materialCode", "物料编码", "string").variable("lines.materialName", "名称", "string").variable("lines.customerPartNo", "客户料号", "string")
                .variable("lines.qty", "数量", "qty").variable("lines.uom", "单位", "string").variable("lines.remark", "备注", "string")
                .sampleData("{\"docNo\":\"SN-202609-0001\",\"customerName\":\"ABC Ltd\",\"lines\":[{\"lineNo\":1,\"materialCode\":\"FG0001\",\"qty\":600}]}");
    }

    @Bean
    public PrintBizDefinition shpPickingPrint() {
        return PrintBizDefinition.of(PICKING, "拣货单", MODULE, "/shipping/pickings/{id}/print-data")
                .variable("docNo", "单号", "string").variable("noticeNo", "出货通知", "string").variable("customerName", "客户", "string")
                .variable("warehouseName", "仓库", "string").variable("shipDate", "出货日期", "date")
                .variable("lines", "明细（按库位排序）", "array").variable("lines.locationCode", "库位", "string").variable("lines.materialCode", "物料", "string")
                .variable("lines.materialName", "名称", "string").variable("lines.batchNo", "批次", "string").variable("lines.suggestedQty", "推荐数量", "qty")
                .sampleData("{\"docNo\":\"PK-20260929-001\",\"lines\":[{\"locationCode\":\"A-01\",\"materialCode\":\"FG0001\",\"suggestedQty\":200}]}");
    }

    @Bean
    public PrintBizDefinition shpCartonLabelPrint() {
        return PrintBizDefinition.of(CARTON_LABEL, "箱唛标签", MODULE, "/shipping/notices/{id}/labels")
                .variable("cartons", "箱", "array").variable("cartons.customerName", "客户", "string").variable("cartons.poNo", "PO", "string")
                .variable("cartons.partNo", "料号", "string").variable("cartons.description", "品名", "string").variable("cartons.qty", "数量", "qty")
                .variable("cartons.grossWeight", "毛重", "number").variable("cartons.netWeight", "净重", "number").variable("cartons.cartonText", "箱号", "string")
                .sampleData("{\"cartons\":[{\"customerName\":\"ABC Ltd\",\"poNo\":\"PO-1\",\"partNo\":\"P-100\",\"qty\":100,\"cartonText\":\"1/6\"}]}");
    }

    @Bean
    public PrintBizDefinition shpShipmentPrint() {
        return PrintBizDefinition.of(SHIPMENT, "出货单 / 送货单", MODULE, "/shipping/shipments/{id}/print-data")
                .variable("docNo", "单号", "string").variable("status", "状态", "string").variable("customerName", "客户", "string")
                .variable("shipDate", "出货日期", "date").variable("shipToText", "收货地址", "string").variable("transportModeName", "运输方式", "string")
                .variable("blNo", "运单号", "string").variable("cartonCount", "箱数", "number")
                .variable("lines", "明细", "array").variable("lines.lineNo", "行号", "number").variable("lines.orderNo", "订单号", "string")
                .variable("lines.materialCode", "物料编码", "string").variable("lines.materialName", "名称", "string").variable("lines.batchNo", "批次", "string")
                .variable("lines.qty", "数量", "qty").variable("lines.uom", "单位", "string")
                .sampleData("{\"docNo\":\"SH-202609-0001\",\"customerName\":\"ABC Ltd\",\"lines\":[{\"lineNo\":1,\"materialCode\":\"FG0001\",\"qty\":600}]}");
    }

    @Bean
    public PrintBizDefinition shpPackingListPrint() {
        return PrintBizDefinition.of(PACKING_LIST, "Packing List", MODULE, "/shipping/packing-lists/{id}/print-data")
                .variable("plNo", "PL No.", "string").variable("plDate", "Date", "date").variable("companyName", "Company", "string")
                .variable("consignee", "Consignee", "string").variable("notifyParty", "Notify Party", "string").variable("shippingMarks", "Marks", "string")
                .variable("lines", "Lines", "array").variable("lines.cartonRange", "Carton No.", "string").variable("lines.description", "Description", "string")
                .variable("lines.partNo", "Part No.", "string").variable("lines.qtyPerCarton", "Qty/Ctn", "qty").variable("lines.cartons", "Ctns", "number")
                .variable("lines.qty", "Qty", "qty").variable("lines.netWeight", "N.W.", "number").variable("lines.grossWeight", "G.W.", "number")
                .variable("lines.cbm", "CBM", "number").variable("totals", "Totals", "object")
                .sampleData("{\"plNo\":\"PL2026090001\",\"lines\":[{\"cartonRange\":\"1-5\",\"description\":\"PCBA\",\"qty\":500}],\"totals\":{\"cartons\":5}}");
    }

    @Bean
    public PrintBizDefinition shpInvoicePrint() {
        return PrintBizDefinition.of(INVOICE, "Commercial Invoice", MODULE, "/shipping/invoices/{id}/print-data")
                .variable("invoiceNo", "Invoice No.", "string").variable("invoiceDate", "Date", "date").variable("companyName", "Company", "string")
                .variable("billTo", "Bill To", "string").variable("consignee", "Consignee", "string").variable("currency", "Currency", "string")
                .variable("tradeTerm", "Trade Term", "string").variable("paymentTermText", "Payment Term", "string").variable("totalAmount", "Total", "amount")
                .variable("amountInWords", "Say", "string").variable("bankInfo", "Bank", "string")
                .variable("lines", "Lines", "array").variable("lines.description", "Description", "string").variable("lines.hsCode", "HS Code", "string")
                .variable("lines.qty", "Qty", "qty").variable("lines.unitPrice", "Unit Price", "price").variable("lines.amount", "Amount", "amount")
                .sampleData("{\"invoiceNo\":\"CI2026090001\",\"currency\":\"USD\",\"totalAmount\":12500,\"amountInWords\":\"SAY US DOLLARS TWELVE THOUSAND FIVE HUNDRED ONLY\"}");
    }
}
