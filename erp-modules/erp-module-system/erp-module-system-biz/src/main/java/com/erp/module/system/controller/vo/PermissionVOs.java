package com.erp.module.system.controller.vo;

import java.util.List;

/** 权限点树（授权抽屉使用）：模块 → 分组 → 权限点 */
public final class PermissionVOs {

    private PermissionVOs() {
    }

    public record ModuleNode(String code, String name, List<GroupNode> groups) {
    }

    public record GroupNode(String code, String name, List<PermissionNode> permissions) {
    }

    /** @param type MENU / BUTTON / FIELD */
    public record PermissionNode(String code, String name, String type, List<String> dependsOn) {
    }
}
