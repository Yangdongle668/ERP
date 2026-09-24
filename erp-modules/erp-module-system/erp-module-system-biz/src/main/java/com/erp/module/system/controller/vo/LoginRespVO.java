package com.erp.module.system.controller.vo;

/** @param expiresIn accessToken 有效秒数 */
public record LoginRespVO(String accessToken, String refreshToken, long expiresIn) {
}
