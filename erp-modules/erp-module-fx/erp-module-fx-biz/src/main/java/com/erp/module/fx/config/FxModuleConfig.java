package com.erp.module.fx.config;

import com.erp.framework.module.ErpModule;
import com.erp.module.system.api.param.ParamDefinition;
import com.erp.module.system.api.param.ParamDefinitions;
import com.erp.module.system.api.permission.PermissionDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 实时汇率模块的声明式注册（需求 16-实时汇率） */
@Configuration
public class FxModuleConfig {

    public static final String MODULE = "fx";
    public static final String P_ENABLED = "fx.enabled";

    @Bean
    public ErpModule fxModule() {
        return new ErpModule(MODULE, "实时汇率", 430);
    }

    @Bean
    public PermissionDefinition fxPermissions() {
        return PermissionDefinition.group(MODULE, "rate", "实时汇率", 10)
                .menu("fx:rate:query", "查看")
                .button("fx:rate:refresh", "立即刷新");
    }

    @Bean
    public ParamDefinitions fxParams() {
        return ParamDefinitions.of(
                ParamDefinition.bool(P_ENABLED, MODULE, "实时汇率", "自动获取汇率", true,
                        "每 15 分钟获取中国银行现汇买入价（美元、欧元、日元、韩元、澳元，只保存美元），失败时按 1、2、4…60 分钟退避重试").sort(10));
    }
}
