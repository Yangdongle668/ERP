package com.erp.module.inventory.controller;

import com.erp.common.result.CommonResult;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.inventory.controller.vo.WarehouseVOs.CategoryWarehouseRow;
import com.erp.module.inventory.controller.vo.WarehouseVOs.CategoryWarehouseSave;
import com.erp.module.inventory.controller.vo.WarehouseVOs.GenerateResult;
import com.erp.module.inventory.controller.vo.WarehouseVOs.LocationGenerate;
import com.erp.module.inventory.controller.vo.WarehouseVOs.LocationRow;
import com.erp.module.inventory.controller.vo.WarehouseVOs.LocationSave;
import com.erp.module.inventory.controller.vo.WarehouseVOs.UsersReq;
import com.erp.module.inventory.controller.vo.WarehouseVOs.WarehouseRow;
import com.erp.module.inventory.controller.vo.WarehouseVOs.WarehouseSave;
import com.erp.module.inventory.controller.vo.WarehouseVOs.WarehouseSimple;
import com.erp.module.inventory.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 仓库与库位、类别默认仓（需求 08-01 第 5 节） */
@Tag(name = "仓库 - 仓库与库位")
@RestController
@RequestMapping("/api/inventory")
public class WarehouseController {

    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @GetMapping("/warehouses")
    @PreAuthorize("@ss.has('inv:warehouse:query')")
    public CommonResult<List<WarehouseRow>> list(@RequestParam(required = false) String keyword, @RequestParam(required = false) WarehouseType type,
                                                 @RequestParam(required = false) String status) {
        return CommonResult.success(service.list(keyword, type, status));
    }

    @Operation(summary = "WarehouseSelect（登录即可）：启用仓库，onlyMine 按仓库数据权限过滤")
    @GetMapping("/warehouses/simple")
    public CommonResult<List<WarehouseSimple>> simple(@RequestParam(required = false) String types, @RequestParam(defaultValue = "true") boolean onlyMine) {
        return CommonResult.success(service.simple(types, onlyMine));
    }

    @PostMapping("/warehouses")
    @PreAuthorize("@ss.has('inv:warehouse:create')")
    public CommonResult<Long> create(@Valid @RequestBody WarehouseSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody WarehouseSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/warehouses/{id}")
    @PreAuthorize("@ss.has('inv:warehouse:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/warehouses/{id}/enable")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        service.enable(id);
        return CommonResult.success();
    }

    @PostMapping("/warehouses/{id}/disable")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        service.disable(id);
        return CommonResult.success();
    }

    @Operation(summary = "设置仓库操作人员")
    @PutMapping("/warehouses/{id}/users")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<Void> users(@PathVariable Long id, @RequestBody UsersReq req) {
        service.setUsers(id, req.userIds());
        return CommonResult.success();
    }

    // ---------- 库位 ----------

    @GetMapping("/warehouses/{id}/locations")
    @PreAuthorize("@ss.has('inv:warehouse:query')")
    public CommonResult<List<LocationRow>> locations(@PathVariable Long id) {
        return CommonResult.success(service.locations(id));
    }

    @PostMapping("/warehouses/{id}/locations")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<Long> createLocation(@PathVariable Long id, @Valid @RequestBody LocationSave req) {
        return CommonResult.success(service.createLocation(id, req));
    }

    @PutMapping("/warehouses/{id}/locations/{locId}")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<Void> updateLocation(@PathVariable Long id, @PathVariable Long locId, @Valid @RequestBody LocationSave req) {
        service.updateLocation(id, locId, req);
        return CommonResult.success();
    }

    @PostMapping("/warehouses/{id}/locations/{locId}/enable")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<Void> enableLocation(@PathVariable Long id, @PathVariable Long locId) {
        service.setLocationStatus(id, locId, true);
        return CommonResult.success();
    }

    @PostMapping("/warehouses/{id}/locations/{locId}/disable")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<Void> disableLocation(@PathVariable Long id, @PathVariable Long locId) {
        service.setLocationStatus(id, locId, false);
        return CommonResult.success();
    }

    @DeleteMapping("/warehouses/{id}/locations/{locId}")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<Void> deleteLocation(@PathVariable Long id, @PathVariable Long locId) {
        service.deleteLocation(id, locId);
        return CommonResult.success();
    }

    @Operation(summary = "批量生成库位（preview=true 只预览）")
    @PostMapping("/warehouses/{id}/locations/batch-generate")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<GenerateResult> generate(@PathVariable Long id, @Valid @RequestBody LocationGenerate req) {
        return CommonResult.success(service.generateLocations(id, req));
    }

    @Operation(summary = "LocationSelect（登录即可）")
    @GetMapping("/locations/simple")
    public CommonResult<List<LocationRow>> simpleLocations(@RequestParam Long warehouseId) {
        return CommonResult.success(service.simpleLocations(warehouseId));
    }

    // ---------- 类别默认仓 ----------

    @GetMapping("/category-warehouses")
    @PreAuthorize("@ss.has('inv:warehouse:query')")
    public CommonResult<List<CategoryWarehouseRow>> categoryWarehouses() {
        return CommonResult.success(service.categoryWarehouses());
    }

    @PostMapping("/category-warehouses")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<Long> createCategoryWarehouse(@Valid @RequestBody CategoryWarehouseSave req) {
        return CommonResult.success(service.saveCategoryWarehouse(null, req));
    }

    @PutMapping("/category-warehouses/{id}")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<Void> updateCategoryWarehouse(@PathVariable Long id, @Valid @RequestBody CategoryWarehouseSave req) {
        service.saveCategoryWarehouse(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/category-warehouses/{id}")
    @PreAuthorize("@ss.has('inv:warehouse:update')")
    public CommonResult<Void> deleteCategoryWarehouse(@PathVariable Long id) {
        service.deleteCategoryWarehouse(id);
        return CommonResult.success();
    }
}
