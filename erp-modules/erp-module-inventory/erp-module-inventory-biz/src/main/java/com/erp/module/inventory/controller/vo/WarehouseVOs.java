package com.erp.module.inventory.controller.vo;

import com.erp.module.inventory.api.warehouse.WarehouseType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 仓库与库位接口的请求/响应（需求 08-01） */
public final class WarehouseVOs {

    private WarehouseVOs() {
    }

    public record WarehouseRow(Long id, String code, String name, WarehouseType warehouseType, boolean available, boolean isDefault,
                               boolean locationEnabled, boolean allowNegative, Long managerId, String managerName, String address,
                               List<Long> userIds, List<String> userNames, int locationCount, String status, String remark, int version) {
    }

    /** WarehouseSelect 选项 */
    public record WarehouseSimple(Long id, String code, String name, WarehouseType warehouseType, boolean available, boolean locationEnabled,
                                  boolean isDefault) {
    }

    public record WarehouseSave(
            @NotBlank(message = "请输入仓库编码") @Pattern(regexp = "^[A-Za-z0-9-]{1,16}$", message = "仓库编码为 1～16 位字母、数字、-") String code,
            @NotBlank(message = "请输入名称") @Size(max = 64) String name,
            @NotNull(message = "请选择仓库类型") WarehouseType warehouseType,
            Long managerId,
            @Size(max = 256) String address,
            Boolean locationEnabled,
            Boolean allowNegative,
            Boolean isDefault,
            @Size(max = 256) String remark,
            Integer version) {
    }

    public record UsersReq(List<Long> userIds) {
    }

    public record LocationRow(Long id, Long warehouseId, String code, String name, String status, String remark, boolean hasStock, int version) {
    }

    public record LocationSave(
            @NotBlank(message = "请输入库位编码") @Pattern(regexp = "^[A-Za-z0-9._-]{1,32}$", message = "库位编码为 1～32 位字母、数字、. _ -") String code,
            @Size(max = 64) String name,
            @Size(max = 128) String remark,
            Integer version) {
    }

    /** 批量生成库位：区（字母范围）- 排 - 层 - 位，如 A-01-02-03；preview=true 只返回将生成的编码 */
    public record LocationGenerate(
            @NotBlank @Pattern(regexp = "^[A-Za-z]$", message = "区为单个字母") String zoneFrom,
            @NotBlank @Pattern(regexp = "^[A-Za-z]$", message = "区为单个字母") String zoneTo,
            @NotNull @Min(1) @Max(99) Integer rowFrom, @NotNull @Min(1) @Max(99) Integer rowTo,
            @NotNull @Min(1) @Max(99) Integer levelFrom, @NotNull @Min(1) @Max(99) Integer levelTo,
            @NotNull @Min(1) @Max(99) Integer posFrom, @NotNull @Min(1) @Max(99) Integer posTo,
            boolean preview) {
    }

    /** codes 为将要（或已）生成的编码；skipped 为已存在而跳过的编码 */
    public record GenerateResult(List<String> codes, List<String> skipped) {
    }

    public record CategoryWarehouseRow(Long id, Long categoryId, String categoryPath, Long warehouseId, String warehouseName,
                                       WarehouseType warehouseType) {
    }

    public record CategoryWarehouseSave(@NotNull(message = "请选择物料类别") Long categoryId, @NotNull(message = "请选择仓库") Long warehouseId) {
    }
}
