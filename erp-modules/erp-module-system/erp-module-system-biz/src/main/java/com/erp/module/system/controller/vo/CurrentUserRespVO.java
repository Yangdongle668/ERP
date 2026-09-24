package com.erp.module.system.controller.vo;

import java.util.Set;

public record CurrentUserRespVO(Long id, String username, String realName, Set<String> permissions) {
}
