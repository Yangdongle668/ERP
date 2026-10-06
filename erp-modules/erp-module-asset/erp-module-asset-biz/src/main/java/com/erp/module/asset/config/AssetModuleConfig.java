package com.erp.module.asset.config;

import com.erp.framework.module.ErpModule;
import com.erp.module.system.api.coderule.CodeRuleDefinition;
import com.erp.module.system.api.coderule.CodeRuleDefinition.ResetCycle;
import com.erp.module.system.api.dict.DictDefinition;
import com.erp.module.system.api.permission.PermissionDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 固定资产模块的声明式注册（需求 15-固定资产） */
@Configuration
public class AssetModuleConfig {

    public static final String MODULE = "asset";
    public static final String BIZ_TYPE = "AST_ASSET";
    /** 编码规则：前缀变量 {asset} = LD1-PD-CPJ-264-，按前缀（公司 + 分类 + 缩写 + 年月）独立计数 */
    public static final String CODE_RULE = "AST_ASSET";
    public static final String DICT_CLASS = "ast_asset_class";
    /** 工厂代码字典（系统管理模块声明）：取末位作为编码中的公司代码 */
    public static final String DICT_FACTORY = "sys_factory";

    @Bean
    public ErpModule assetModule() {
        return new ErpModule(MODULE, "固定资产", 420);
    }

    @Bean
    public CodeRuleDefinition assetCodeRule() {
        return CodeRuleDefinition.withVars(CODE_RULE, "固定资产编码", MODULE, "{asset}", "", "", 3, ResetCycle.NEVER, "asset");
    }

    @Bean
    public PermissionDefinition assetPermissions() {
        return PermissionDefinition.group(MODULE, "asset", "固定资产", 10)
                .menu("ast:asset:query", "查看")
                .button("ast:asset:create", "新建")
                .button("ast:asset:update", "编辑 / 状态变更")
                .button("ast:asset:scrap", "报废")
                .button("ast:asset:delete", "删除");
    }

    /** 固定资产分类（《编码规则管理制度》表 1），值即编码中的分类代码 */
    @Bean
    public DictDefinition assetClassDict() {
        return DictDefinition.of(DICT_CLASS, "固定资产分类", MODULE)
                .builtin("PD", "生产专用设备", "Production equipment")
                .builtin("QA", "品质专用设备与精密仪器", "Quality equipment & instruments")
                .builtin("EN", "动力/辅料/办公设备", "Utility, tooling & office equipment")
                .builtin("PU", "运输及仓储设备", "Transport & storage equipment")
                .builtin("HR", "行政用品", "Administrative supplies")
                .builtin("GM", "房屋及构筑物", "Buildings & structures")
                .builtin("CU", "客户资产", "Customer-owned assets");
    }
}
