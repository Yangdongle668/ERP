package com.erp.module.system.controller.vo;

import jakarta.validation.constraints.NotBlank;

public record RefreshReqVO(@NotBlank String refreshToken) {
}
