package com.erp.module.engineering.config;

import com.erp.framework.module.ErpModule;
import com.erp.module.engineering.dal.mapper.MaterialMapper;
import com.erp.module.system.api.coderule.CodeRuleDefinition;
import com.erp.module.system.api.permission.PermissionDefinition;
import com.erp.module.system.api.uom.UomReferenceChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineeringModuleConfig {

    public static final String CODE_RULE_MATERIAL = "MATERIAL";

    @Bean
    public ErpModule engineeringModule() {
        return new ErpModule("engineering", "研发工程", 110);
    }

    /** 物料编码默认规则：M + 6 位流水，如 M000001。管理员可在系统管理中修改。 */
    @Bean
    public CodeRuleDefinition materialCodeRule() {
        return new CodeRuleDefinition(CODE_RULE_MATERIAL, "物料编码", "engineering", "M", "", "", 6,
                CodeRuleDefinition.ResetCycle.NEVER, true, java.util.List.of());
    }

    /** 物料权限点（05-研发工程 README 权限点汇总中已实现的部分） */
    @Bean
    public PermissionDefinition materialPermissions() {
        return PermissionDefinition.group("engineering", "material", "物料", 20)
                .menu("eng:material:query", "查看")
                .button("eng:material:create", "新建")
                .button("eng:material:update", "编辑")
                .button("eng:material:enable", "启用")
                .button("eng:material:disable", "停用")
                .button("eng:material:delete", "删除");
    }

    /** 被物料使用的计量单位不能删除、不能修改类别（01-06 SYS-UOM-R03、R04） */
    @Bean
    public UomReferenceChecker materialUomReferenceChecker(MaterialMapper materialMapper) {
        return uom -> materialMapper.countByUom(uom) > 0;
    }
}
