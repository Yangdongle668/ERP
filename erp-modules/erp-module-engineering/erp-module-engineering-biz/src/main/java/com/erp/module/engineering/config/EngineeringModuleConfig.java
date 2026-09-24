package com.erp.module.engineering.config;

import com.erp.framework.module.ErpModule;
import com.erp.module.system.api.coderule.CodeRuleDefinition;
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
        return new CodeRuleDefinition(CODE_RULE_MATERIAL, "物料编码", "M", "", 6, CodeRuleDefinition.ResetCycle.NEVER);
    }
}
