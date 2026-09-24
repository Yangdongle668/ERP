package com.erp.module.system.api.permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 权限点声明（01-03 角色与权限 1.1）：模块 → 分组（通常对应一个菜单页面）→ 权限点。
 * 各模块在配置类中声明为 Bean，系统管理模块启动时同步到 sys_permission。
 *
 * <pre>{@code
 * @Bean
 * public PermissionDefinition materialPermissions() {
 *     return PermissionDefinition.group("engineering", "material", "物料", 20)
 *             .menu("eng:material:query", "查看")
 *             .button("eng:material:create", "新建")
 *             .field("eng:material:cost", "查看成本");
 * }
 * }</pre>
 * 按钮、字段权限默认依赖该分组的菜单权限（授权时自动勾选“查看”）。
 */
public final class PermissionDefinition {

    public enum Type { MENU, BUTTON, FIELD }

    /** @param dependsOn 依赖的权限点 */
    public record Item(String code, String name, Type type, List<String> dependsOn, int sort) {
    }

    private final String moduleCode;
    private final String groupCode;
    private final String groupName;
    private final int groupSort;
    private final List<Item> items = new ArrayList<>();

    private PermissionDefinition(String moduleCode, String groupCode, String groupName, int groupSort) {
        this.moduleCode = moduleCode;
        this.groupCode = groupCode;
        this.groupName = groupName;
        this.groupSort = groupSort;
    }

    /**
     * @param groupSort 分组在模块内的排序（与菜单顺序一致）
     */
    public static PermissionDefinition group(String moduleCode, String groupCode, String groupName, int groupSort) {
        return new PermissionDefinition(moduleCode, groupCode, groupName, groupSort);
    }

    public PermissionDefinition menu(String code, String name) {
        return add(code, name, Type.MENU, List.of());
    }

    public PermissionDefinition button(String code, String name, String... dependsOn) {
        return add(code, name, Type.BUTTON, List.of(dependsOn));
    }

    public PermissionDefinition field(String code, String name, String... dependsOn) {
        return add(code, name, Type.FIELD, List.of(dependsOn));
    }

    private PermissionDefinition add(String code, String name, Type type, List<String> dependsOn) {
        if (code == null || !code.matches("[a-z][a-z0-9-]*(:[a-z0-9-]+)+")) {
            throw new IllegalArgumentException("权限标识格式应为 模块:资源:动作，实际: " + code);
        }
        List<String> deps = new ArrayList<>(dependsOn);
        if (type != Type.MENU) {
            items.stream().filter(i -> i.type() == Type.MENU).findFirst()
                    .ifPresent(menu -> {
                        if (!deps.contains(menu.code())) deps.add(0, menu.code());
                    });
        }
        items.add(new Item(code, name, type, List.copyOf(deps), items.size() * 10));
        return this;
    }

    public String moduleCode() {
        return moduleCode;
    }

    public String groupCode() {
        return groupCode;
    }

    public String groupName() {
        return groupName;
    }

    public int groupSort() {
        return groupSort;
    }

    public List<Item> items() {
        return Collections.unmodifiableList(items);
    }
}
