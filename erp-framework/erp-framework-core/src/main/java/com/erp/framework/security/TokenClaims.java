package com.erp.framework.security;

/** 令牌中的声明：用户 ID 与令牌版本（tv） */
public record TokenClaims(Long userId, int tokenVersion) {
}
