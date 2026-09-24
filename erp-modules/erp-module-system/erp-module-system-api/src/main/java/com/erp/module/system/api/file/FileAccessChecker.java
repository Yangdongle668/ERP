package com.erp.module.system.api.file;

/**
 * 附件访问控制扩展点（需求 SYS-FIL-R04）：业务模块为自己的单据类型提供实现（Spring Bean），
 * 判断当前用户能否查看、编辑某张单据的附件（通常按单据查看权限 + 单据状态判断）。
 *
 * <p>没有实现支持的业务类型：只允许上传人和管理员查看、删除。
 */
public interface FileAccessChecker {

    /** 是否负责该业务类型。 */
    boolean supports(String bizType);

    /** 当前用户能否查看（下载、预览）该单据的附件。 */
    boolean canView(String bizType, Long bizId);

    /** 当前用户能否上传、删除该单据的附件。 */
    boolean canEdit(String bizType, Long bizId);
}
