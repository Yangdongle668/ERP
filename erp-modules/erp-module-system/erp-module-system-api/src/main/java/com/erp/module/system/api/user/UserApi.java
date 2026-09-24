package com.erp.module.system.api.user;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 用户查询（审批人解析、单据显示经办人姓名等）。 */
public interface UserApi {

    Optional<UserDTO> get(Long id);

    /** 批量查询，结果按 ID 索引；不存在的 ID 不出现在结果中 */
    Map<Long, UserDTO> list(Collection<Long> ids);

    /** 按用户名查询（不区分大小写），导入时解析“采购员用户名”等列 */
    Optional<UserDTO> getByUsername(String username);

    /** 部门负责人（组织的负责人字段）；未设置时为空 */
    Optional<UserDTO> getDeptLeader(Long deptId);

    /** 直属上级；未设置时为空 */
    Optional<UserDTO> getSuperior(Long userId);

    /** 拥有某角色的启用用户 */
    List<UserDTO> listByRole(Long roleId);

    /** 拥有某权限的启用用户（含超级管理员），用于发送提醒 */
    List<UserDTO> listByPermission(String permission);
}
