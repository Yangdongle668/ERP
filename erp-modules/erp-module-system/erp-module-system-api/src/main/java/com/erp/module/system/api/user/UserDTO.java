package com.erp.module.system.api.user;

/** 用户（对其他模块公开的字段） */
public record UserDTO(Long id, String username, String realName, String employeeNo, Long orgId, Long deptId,
                      String deptName, String mobile, String email, boolean enabled) {
}
