package com.erp.module.crm.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 客户料号对照（需求 03-02） */
public final class PartVOs {

    private PartVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PartQuery extends PageParam {
        private Long customerId;
        /** 前缀匹配 */
        private String customerPartNo;
        private Long materialId;
        private String status;
    }

    public record PartRow(Long id, Long customerId, String customerCode, String customerShortName, String customerPartNo, String customerPartName,
                          String customerPartSpec, String customerRevision, Long materialId, String materialCode, String materialName,
                          String materialSpec, String status, String remark, LocalDateTime updatedAt, int version) {
    }

    public record PartSave(@NotNull(message = "请选择客户") Long customerId,
                           @NotBlank(message = "请填写客户料号") @Size(max = 64) String customerPartNo,
                           @Size(max = 256) String customerPartName, @Size(max = 512) String customerPartSpec,
                           @Size(max = 16) String customerRevision, @NotNull(message = "请选择本厂物料") Long materialId,
                           @Size(max = 256) String remark, Integer version) {
    }
}
