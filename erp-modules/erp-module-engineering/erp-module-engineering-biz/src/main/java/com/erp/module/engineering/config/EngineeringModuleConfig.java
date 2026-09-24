package com.erp.module.engineering.config;

import com.erp.framework.module.ErpModule;
import com.erp.module.engineering.dal.mapper.MaterialMapper;
import com.erp.module.engineering.dal.mapper.MaterialUomMapper;
import com.erp.module.system.api.coderule.CodeRuleDefinition;
import com.erp.module.system.api.coderule.CodeRuleDefinition.ResetCycle;
import com.erp.module.system.api.dict.DictDefinition;
import com.erp.module.system.api.param.ParamDefinition;
import com.erp.module.system.api.param.ParamDefinitions;
import com.erp.module.system.api.permission.PermissionDefinition;
import com.erp.module.system.api.print.PrintBizDefinition;
import com.erp.module.system.api.uom.UomReferenceChecker;
import com.erp.module.system.api.workflow.ApprovalBizDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** 研发工程模块的声明式注册（需求 05-研发工程 README 第 5～9、11 节） */
@Configuration
public class EngineeringModuleConfig {

    public static final String MODULE = "engineering";
    public static final String CODE_RULE_MATERIAL = "ENG_MATERIAL";

    @Bean
    public ErpModule engineeringModule() {
        return new ErpModule(MODULE, "研发工程", 110);
    }

    // ==================== 编码规则 ====================

    /** 物料编码：{categoryPrefix} + 5 位流水，不同类别前缀独立计数，如 FPC00001（允许手工输入） */
    @Bean
    public CodeRuleDefinition materialCodeRule() {
        return CodeRuleDefinition.withVars(CODE_RULE_MATERIAL, "物料编码", MODULE, "{categoryPrefix}", "", "", 5, ResetCycle.NEVER, "categoryPrefix")
                .manual(true);
    }

    @Bean
    public CodeRuleDefinition projectCodeRule() {
        return CodeRuleDefinition.of("ENG_PROJECT", "研发项目", MODULE, "PRJ-", "yyyy", "-", 3, ResetCycle.YEAR);
    }

    @Bean
    public CodeRuleDefinition ecnCodeRule() {
        return CodeRuleDefinition.of("ENG_ECN", "ECN", MODULE, "ECN-", "yyyyMM", "-", 3, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition sampleCodeRule() {
        return CodeRuleDefinition.of("ENG_SAMPLE", "样品单", MODULE, "SP-", "yyyyMM", "-", 4, ResetCycle.MONTH);
    }

    @Bean
    public CodeRuleDefinition toolingCodeRule() {
        return CodeRuleDefinition.of("ENG_TOOLING", "工装编号", MODULE, "T", "", "", 5, ResetCycle.NEVER).manual(true);
    }

    // ==================== 权限点 ====================

    @Bean
    public PermissionDefinition categoryPermissions() {
        return PermissionDefinition.group(MODULE, "category", "物料类别", 10)
                .menu("eng:category:query", "查看")
                .button("eng:category:create", "新建")
                .button("eng:category:update", "编辑")
                .button("eng:category:delete", "删除");
    }

    @Bean
    public PermissionDefinition materialPermissions() {
        return PermissionDefinition.group(MODULE, "material", "物料", 20)
                .menu("eng:material:query", "查看")
                .button("eng:material:create", "新建")
                .button("eng:material:update", "编辑")
                .button("eng:material:enable", "启用")
                .button("eng:material:disable", "停用")
                .button("eng:material:delete", "删除")
                .button("eng:material:import", "导入")
                .button("eng:material:export", "导出")
                .field("eng:material:cost", "查看标准成本");
    }

    @Bean
    public PermissionDefinition bomPermissions() {
        return PermissionDefinition.group(MODULE, "bom", "BOM", 30)
                .menu("eng:bom:query", "查看")
                .button("eng:bom:create", "新建")
                .button("eng:bom:update", "编辑")
                .button("eng:bom:delete", "删除")
                .button("eng:bom:submit", "提交")
                .button("eng:bom:approve", "审核")
                .button("eng:bom:unapprove", "反审核")
                .button("eng:bom:disable", "停用")
                .button("eng:bom:set-default", "设为默认")
                .button("eng:bom:import", "导入")
                .button("eng:bom:export", "导出")
                .button("eng:bom:print", "打印")
                .field("eng:bom:cost", "查看成本");
    }

    // ==================== 字典 ====================

    @Bean
    public DictDefinition operationDict() {
        return DictDefinition.of("eng_operation", "工序", MODULE)
                .item("SMT", "SMT 贴片", "SMT").item("DIP", "DIP 插件", "DIP").item("WELD", "焊接", "Welding")
                .item("ASSY", "组装", "Assembly").item("TEST", "测试", "Test").item("PACK", "包装", "Packing");
    }

    @Bean
    public DictDefinition ecnTypeDict() {
        return DictDefinition.of("eng_ecn_type", "ECN 类型", MODULE)
                .builtin("DESIGN", "设计变更", "Design").builtin("PROCESS", "工艺变更", "Process")
                .builtin("SUBSTITUTE", "物料替代", "Substitute").builtin("DOCUMENT", "文件变更", "Document");
    }

    @Bean
    public DictDefinition ecnReasonDict() {
        return DictDefinition.of("eng_ecn_reason", "ECN 原因", MODULE)
                .item("CUSTOMER", "客户要求", "Customer request").item("COST", "降本", "Cost reduction")
                .item("QUALITY", "质量改善", "Quality").item("SUPPLY", "供应问题", "Supply").item("DESIGN", "设计优化", "Design");
    }

    @Bean
    public DictDefinition projectStageDict() {
        return DictDefinition.of("eng_project_stage", "项目阶段", MODULE)
                .builtin("CONCEPT", "概念", "Concept").builtin("DESIGN", "设计", "Design").builtin("EVT", "工程验证", "EVT")
                .builtin("DVT", "设计验证", "DVT").builtin("PVT", "生产验证", "PVT").builtin("MP", "量产", "MP");
    }

    @Bean
    public DictDefinition toolingTypeDict() {
        return DictDefinition.of("eng_tooling_type", "工装类型", MODULE)
                .builtin("MOLD", "模具", "Mold").builtin("JIG", "治具", "Jig").builtin("FIXTURE", "夹具", "Fixture")
                .builtin("GAUGE", "检具", "Gauge").builtin("STENCIL", "钢网", "Stencil");
    }

    @Bean
    public DictDefinition certTypeDict() {
        DictDefinition d = DictDefinition.of("eng_cert_type", "认证类型", MODULE);
        for (String c : List.of("CE", "UL", "FCC", "CCC", "ROHS", "REACH", "KC", "PSE", "ISO9001")) d.item(c, c, c);
        return d.item("OTHER", "其他", "Other");
    }

    // ==================== 系统参数 ====================

    @Bean
    public ParamDefinitions engineeringParams() {
        return ParamDefinitions.of(
                ParamDefinition.bool("eng.material.enable-approval", MODULE, "物料", "物料启用需要审批", false,
                        "是：草稿物料启用时走审批流 ENG_MATERIAL；否：有权限者直接启用").sort(10),
                ParamDefinition.enumOf("eng.material.duplicate-check", MODULE, "物料", "物料查重方式", "WARN",
                        List.of(new ParamDefinition.Option("OFF", "不查重"), new ParamDefinition.Option("WARN", "提示但允许保存"),
                                new ParamDefinition.Option("BLOCK", "禁止保存")),
                        "同一类别下名称+规格相同，或制造商料号相同视为疑似重复").sort(20),
                ParamDefinition.integer("eng.bom.max-level", MODULE, "BOM", "BOM 最大层数", 20, 5, 30, "多级展开、提交校验的层数上限").sort(10),
                ParamDefinition.bool("eng.bom.allow-draft-component", MODULE, "BOM", "BOM 允许使用草稿物料", true,
                        "仅草稿 BOM 可用；提交审核时仍要求全部子件已启用").sort(20),
                ParamDefinition.decimal("eng.tooling.life-warn-pct", MODULE, "工装", "工装寿命预警比例", "90", "50", "99",
                        "已用次数达到寿命的该百分比时预警").sort(10),
                ParamDefinition.string("eng.cert.remind-days", MODULE, "认证", "证书到期提醒天数", "90,30,7", "逗号分隔").sort(10));
    }

    // ==================== 审批与打印 ====================

    @Bean
    public ApprovalBizDefinition materialApproval() {
        return ApprovalBizDefinition.of("ENG_MATERIAL", "物料启用", MODULE, "/engineering/material/{id}")
                .enumField("materialType", "物料类型", List.of(
                        new ApprovalBizDefinition.Option("RAW", "原材料"), new ApprovalBizDefinition.Option("SEMI_FINISHED", "半成品"),
                        new ApprovalBizDefinition.Option("FINISHED", "成品"), new ApprovalBizDefinition.Option("PACKAGING", "包材"),
                        new ApprovalBizDefinition.Option("AUXILIARY", "辅料"), new ApprovalBizDefinition.Option("PHANTOM", "虚拟件")))
                .stringField("categoryId", "物料类别")
                .userField("createdBy", "申请人");
    }

    @Bean
    public ApprovalBizDefinition bomApproval() {
        return ApprovalBizDefinition.of("ENG_BOM", "BOM", MODULE, "/engineering/bom/{id}")
                .enumField("parentMaterialType", "父件类型", List.of(
                        new ApprovalBizDefinition.Option("SEMI_FINISHED", "半成品"), new ApprovalBizDefinition.Option("FINISHED", "成品"),
                        new ApprovalBizDefinition.Option("PHANTOM", "虚拟件")))
                .numberField("lineCount", "行数");
    }

    /** BOM 清单打印；内置模板 print-templates/ENG_BOM-zh-CN.html */
    @Bean
    public PrintBizDefinition bomPrint() {
        return PrintBizDefinition.of("ENG_BOM", "BOM 清单", MODULE, "/engineering/boms/{id}/print-data")
                .variable("docNo", "BOM 编号", "string")
                .variable("version", "版本", "string")
                .variable("status", "状态", "string")
                .variable("effectiveDate", "生效日期", "date")
                .variable("baseQty", "基数", "qty")
                .variable("description", "版本说明", "string")
                .variable("remark", "备注", "string")
                .variable("material", "父件", "object")
                .variable("material.code", "父件编码", "string")
                .variable("material.name", "父件名称", "string")
                .variable("material.spec", "父件规格", "string")
                .variable("material.uom", "父件单位", "string")
                .variable("lines", "明细", "array")
                .variable("lines.lineNo", "行号", "number")
                .variable("lines.code", "子件编码", "string")
                .variable("lines.name", "子件名称", "string")
                .variable("lines.spec", "规格", "string")
                .variable("lines.uom", "单位", "string")
                .variable("lines.qtyPer", "用量", "qty")
                .variable("lines.scrapRatePct", "损耗率(%)", "number")
                .variable("lines.positionNo", "位号", "string")
                .variable("lines.issueMethod", "发料方式", "string")
                .variable("lines.substitutes", "替代料", "string")
                .variable("lines.remark", "备注", "string")
                .variable("lineCount", "行数", "number")
                .sampleData("""
                        {"docNo":"FG00001-V2","version":"V2","status":"已审核","effectiveDate":"2026-09-24","baseQty":1,
                         "description":"改用 B 供应商连接器","remark":"",
                         "material":{"code":"FG00001","name":"蓝牙耳机","spec":"BT-100 黑色","uom":"PCS"},
                         "lines":[{"lineNo":1,"code":"SF00001","name":"PCBA 主板","spec":"BT-100-MB","uom":"PCS","qtyPer":1,"scrapRatePct":0,"positionNo":"","issueMethod":"领料","substitutes":"","remark":""},
                                  {"lineNo":2,"code":"RAW00012","name":"外壳","spec":"黑色 ABS","uom":"PCS","qtyPer":1,"scrapRatePct":1,"positionNo":"","issueMethod":"领料","substitutes":"","remark":""},
                                  {"lineNo":3,"code":"RAW00031","name":"螺丝","spec":"M1.6×4","uom":"PCS","qtyPer":4,"scrapRatePct":2,"positionNo":"","issueMethod":"倒冲","substitutes":"RAW00032 螺丝（镀镍）","remark":""}],
                         "lineCount":3}
                        """);
    }

    // ==================== 引用检查 ====================

    /** 被物料使用（基本/采购/销售单位、单位换算）的计量单位不能删除、不能修改类别（01-06 SYS-UOM-R03、R04） */
    @Bean
    public UomReferenceChecker materialUomReferenceChecker(MaterialMapper materialMapper, MaterialUomMapper uomMapper) {
        return uom -> materialMapper.countByUom(uom) > 0 || uomMapper.countByUom(uom) > 0;
    }
}
