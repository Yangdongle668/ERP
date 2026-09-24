package com.erp.module.system.api.dict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 模块内置字典声明（01-04 数据字典）。启动时：类型不存在则创建；内置项不存在则补充；
 * 不覆盖管理员对标签、颜色、排序的修改。
 *
 * <pre>{@code
 * @Bean
 * public DictDefinition tradeTermDict() {
 *     return DictDefinition.of("sys_trade_term", "贸易条款", "system")
 *             .builtin("EXW", "EXW 工厂交货", "EX Works")
 *             .builtin("FOB", "FOB 船上交货", "Free On Board");
 * }
 * }</pre>
 */
public final class DictDefinition {

    /** 标签颜色，对应前端 DictTag */
    public enum TagType { DEFAULT, PRIMARY, SUCCESS, WARNING, DANGER, INFO }

    /** @param builtin 内置项：程序逻辑依赖其取值，不可删除、不可停用、值不可改 */
    public record Item(String value, String label, String labelEn, TagType tagType, boolean builtin, boolean isDefault) {
    }

    private final String typeCode;
    private final String name;
    private final String moduleCode;
    private final List<Item> items = new ArrayList<>();

    private DictDefinition(String typeCode, String name, String moduleCode) {
        if (typeCode == null || !typeCode.matches("[a-z][a-z0-9_]{1,63}")) {
            throw new IllegalArgumentException("字典类型编码必须为小写字母开头的小写字母、数字、下划线: " + typeCode);
        }
        this.typeCode = typeCode;
        this.name = name;
        this.moduleCode = moduleCode;
    }

    public static DictDefinition of(String typeCode, String name, String moduleCode) {
        return new DictDefinition(typeCode, name, moduleCode);
    }

    /** 内置项（程序逻辑依赖） */
    public DictDefinition builtin(String value, String label, String labelEn) {
        return add(new Item(value, label, labelEn, TagType.DEFAULT, true, false));
    }

    /** 初始项（首次创建时写入，之后管理员可以停用或删除） */
    public DictDefinition item(String value, String label, String labelEn) {
        return add(new Item(value, label, labelEn, TagType.DEFAULT, false, false));
    }

    public DictDefinition add(Item item) {
        if (item.value() == null || !item.value().matches("[A-Z0-9_]{1,32}")) {
            throw new IllegalArgumentException("字典值必须为 1~32 位大写字母、数字、下划线: " + item.value());
        }
        items.add(item);
        return this;
    }

    public String typeCode() {
        return typeCode;
    }

    public String name() {
        return name;
    }

    public String moduleCode() {
        return moduleCode;
    }

    public List<Item> items() {
        return Collections.unmodifiableList(items);
    }
}
