package com.erp.module.system.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/** 数据字典 VO */
public final class DictVOs {

    private DictVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TypeQuery extends PageParam {
        private String keyword;
        private String moduleCode;
    }

    public record TypeResp(Long id, String code, String name, String moduleCode, String moduleName, boolean builtin,
                           String status, String remark, Integer version, LocalDateTime updatedAt) {
    }

    public record TypeSave(
            @NotBlank(message = "请输入编码") @Pattern(regexp = "[a-z][a-z0-9_]{1,63}", message = "编码格式不正确，2～64 位，小写字母开头，小写字母数字下划线") String code,
            @NotBlank(message = "请输入名称") @Size(max = 64) String name,
            String status,
            @Size(max = 256) String remark,
            Integer version) {
    }

    public record ItemResp(Long id, String typeCode, String value, String label, String labelEn, String tagType, int sort,
                           boolean isDefault, boolean builtin, String status, String remark, Integer version) {
    }

    public record ItemSave(
            @NotBlank(message = "请选择字典类型") String typeCode,
            @NotBlank(message = "请输入值") @Pattern(regexp = "[A-Za-z0-9_]{1,32}", message = "值格式不正确，1～32 位字母数字下划线") String value,
            @NotBlank(message = "请输入标签") @Size(max = 64) String label,
            @Size(max = 128) String labelEn,
            @NotBlank String tagType,
            @NotNull Integer sort,
            boolean isDefault,
            @Size(max = 256) String remark,
            Integer version) {
    }

    /** 前端全局缓存：全部启用类型的全部项（停用项带 enabled=false，用于显示历史数据标签） */
    public record DictBundle(long version, List<BundleType> types) {
    }

    public record BundleType(String code, String name, List<BundleItem> items) {
    }

    public record BundleItem(String value, String label, String labelEn, String tagType, int sort, boolean isDefault, boolean enabled) {
    }
}
