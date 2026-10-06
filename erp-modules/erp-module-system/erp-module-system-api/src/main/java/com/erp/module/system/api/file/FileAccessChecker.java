package com.erp.module.system.api.file;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 附件访问控制扩展点（需求 SYS-FIL-R04）：业务模块为自己的单据类型提供实现（Spring Bean），
 * 判断当前用户能否查看、编辑某张单据的附件（通常按单据查看权限 + 单据状态判断）。
 *
 * <p>没有实现支持的业务类型：只允许上传人和管理员查看、删除，也只有管理员能直接上传到该类单据。
 */
public interface FileAccessChecker {

    /** 是否负责该业务类型。 */
    boolean supports(String bizType);

    /** 当前用户能否查看（下载、预览）该单据的附件。 */
    boolean canView(String bizType, Long bizId);

    /** 当前用户能否上传、删除该单据的附件。 */
    boolean canEdit(String bizType, Long bizId);

    /**
     * 按权限标识判断的实现：具备任一查看权限可查看，具备任一编辑权限可上传、删除。
     *
     * @param rules         业务类型 → 权限规则
     * @param hasPermission 当前用户是否具备某权限（由业务模块传入，api 不依赖框架）
     */
    static FileAccessChecker byPermissions(Map<String, Rule> rules, Predicate<String> hasPermission) {
        return new FileAccessChecker() {
            @Override
            public boolean supports(String bizType) {
                return rules.containsKey(bizType);
            }

            @Override
            public boolean canView(String bizType, Long bizId) {
                return rules.get(bizType).view().stream().anyMatch(hasPermission);
            }

            @Override
            public boolean canEdit(String bizType, Long bizId) {
                return rules.get(bizType).edit().stream().anyMatch(hasPermission);
            }
        };
    }

    /** 权限规则：查看权限、编辑权限（满足其一即可） */
    record Rule(List<String> view, List<String> edit) {

        public static Rule of(String view, String... edit) {
            return new Rule(List.of(view), List.of(edit));
        }
    }
}
