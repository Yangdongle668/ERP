package com.erp.module.system.api.param;

import java.util.List;

/**
 * 系统参数声明（01-10 系统参数）。启动时参数不存在则按默认值创建；已存在的不覆盖值，但更新名称、说明、选项。
 *
 * <pre>{@code
 * @Bean
 * public ParamDefinition allowNegativeStock() {
 *     return ParamDefinition.bool("inv.stock.allow-negative", "inventory", "库存控制", "允许负库存", false,
 *             "开启后出库数量可以大于库存数量。建议关闭。");
 * }
 * }</pre>
 *
 * @param key          参数编码，格式 模块.分组.名称
 * @param options      ENUM 选项
 * @param minValue     INT/DECIMAL 最小值（含），可为空
 * @param maxValue     INT/DECIMAL 最大值（含），可为空
 */
public record ParamDefinition(String key, String moduleCode, String groupName, String name, ParamType type,
                              String defaultValue, String description, List<Option> options,
                              String minValue, String maxValue, int sort) {

    public record Option(String value, String label) {
    }

    public ParamDefinition {
        if (key == null || !key.matches("[a-z][a-z0-9-]*(\\.[a-z0-9-]+)+")) {
            throw new IllegalArgumentException("参数编码格式应为 模块.分组.名称: " + key);
        }
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static ParamDefinition string(String key, String module, String group, String name, String def, String desc) {
        return new ParamDefinition(key, module, group, name, ParamType.STRING, def, desc, List.of(), null, null, 0);
    }

    public static ParamDefinition integer(String key, String module, String group, String name, int def, int min, int max, String desc) {
        return new ParamDefinition(key, module, group, name, ParamType.INT, String.valueOf(def), desc, List.of(),
                String.valueOf(min), String.valueOf(max), 0);
    }

    public static ParamDefinition decimal(String key, String module, String group, String name, String def, String min, String max, String desc) {
        return new ParamDefinition(key, module, group, name, ParamType.DECIMAL, def, desc, List.of(), min, max, 0);
    }

    public static ParamDefinition bool(String key, String module, String group, String name, boolean def, String desc) {
        return new ParamDefinition(key, module, group, name, ParamType.BOOL, String.valueOf(def), desc, List.of(), null, null, 0);
    }

    public static ParamDefinition enumOf(String key, String module, String group, String name, String def, List<Option> options, String desc) {
        return new ParamDefinition(key, module, group, name, ParamType.ENUM, def, desc, options, null, null, 0);
    }

    public static ParamDefinition userList(String key, String module, String group, String name, String def, String desc) {
        return new ParamDefinition(key, module, group, name, ParamType.USER_LIST, def, desc, List.of(), null, null, 0);
    }

    public static ParamDefinition time(String key, String module, String group, String name, String def, String desc) {
        return new ParamDefinition(key, module, group, name, ParamType.TIME, def, desc, List.of(), null, null, 0);
    }

    /** 页面上的排序（同一分组内升序） */
    public ParamDefinition sort(int s) {
        return new ParamDefinition(key, moduleCode, groupName, name, type, defaultValue, description, options, minValue, maxValue, s);
    }
}
