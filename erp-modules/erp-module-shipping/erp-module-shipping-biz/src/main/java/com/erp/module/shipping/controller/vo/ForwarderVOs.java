package com.erp.module.shipping.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/** 货代（11-05 第 2 节） */
public final class ForwarderVOs {

    private ForwarderVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ForwarderQuery extends PageParam {
        private String keyword;
        /** ENABLED / DISABLED */
        private String status;
        /** SEA / AIR / EXPRESS … */
        private String service;
    }

    public record ForwarderRow(Long id, String code, String name, String contact, String phone, String email, List<String> services, String status,
                               String remark, LocalDateTime createdAt) {
    }

    public record ForwarderSave(@NotBlank String code, @NotBlank String name, String contact, String phone, String email, List<String> services,
                                String status, String remark) {
    }

    public record ForwarderOption(Long id, String code, String name) {
    }
}
