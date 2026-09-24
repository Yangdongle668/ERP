package com.erp.module.system.api.param;

import java.util.List;

/**
 * 一组系统参数声明（一个模块的参数较多时用一个 Bean 声明全部）：
 * <pre>{@code
 * @Bean
 * public ParamDefinitions inventoryParams() {
 *     return ParamDefinitions.of(ParamDefinition.bool(...), ParamDefinition.integer(...));
 * }
 * }</pre>
 */
public record ParamDefinitions(List<ParamDefinition> items) {

    public static ParamDefinitions of(ParamDefinition... items) {
        return new ParamDefinitions(List.of(items));
    }
}
