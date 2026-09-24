package com.erp.module.engineering.controller.vo;

import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;

import java.time.LocalDateTime;

public record MaterialRespVO(
        Long id,
        String code,
        String name,
        String nameEn,
        String spec,
        MaterialType materialType,
        Long categoryId,
        String baseUom,
        MaterialStatus status,
        String remark,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
