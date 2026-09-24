package com.erp.module.system.controller;

import com.erp.common.enums.EnableStatus;
import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.operlog.OperLog;
import com.erp.module.system.controller.vo.DictVOs.DictBundle;
import com.erp.module.system.controller.vo.DictVOs.ItemResp;
import com.erp.module.system.controller.vo.DictVOs.ItemSave;
import com.erp.module.system.controller.vo.DictVOs.TypeQuery;
import com.erp.module.system.controller.vo.DictVOs.TypeResp;
import com.erp.module.system.controller.vo.DictVOs.TypeSave;
import com.erp.module.system.service.DictService;
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

/** 数据字典（01-04） */
@Tag(name = "系统管理 - 数据字典")
@RestController
@RequestMapping("/api/system")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping("/dict-types")
    @PreAuthorize("@ss.has('system:dict:query')")
    public CommonResult<PageResult<TypeResp>> types(@Valid TypeQuery q) {
        return CommonResult.success(dictService.pageTypes(q));
    }

    @OperLog("新建字典类型")
    @PostMapping("/dict-types")
    @PreAuthorize("@ss.has('system:dict:create')")
    public CommonResult<Long> createType(@Valid @RequestBody TypeSave req) {
        return CommonResult.success(dictService.createType(req));
    }

    @OperLog("修改字典类型")
    @PutMapping("/dict-types/{id}")
    @PreAuthorize("@ss.has('system:dict:update')")
    public CommonResult<Void> updateType(@PathVariable Long id, @Valid @RequestBody TypeSave req) {
        dictService.updateType(id, req);
        return CommonResult.success();
    }

    @OperLog("删除字典类型")
    @DeleteMapping("/dict-types/{id}")
    @PreAuthorize("@ss.has('system:dict:delete')")
    public CommonResult<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return CommonResult.success();
    }

    @GetMapping("/dict-items")
    @PreAuthorize("@ss.has('system:dict:query')")
    public CommonResult<List<ItemResp>> items(@RequestParam String typeCode) {
        return CommonResult.success(dictService.listItems(typeCode));
    }

    @OperLog("新建字典项")
    @PostMapping("/dict-items")
    @PreAuthorize("@ss.has('system:dict:create')")
    public CommonResult<Long> createItem(@Valid @RequestBody ItemSave req) {
        return CommonResult.success(dictService.createItem(req));
    }

    @OperLog("修改字典项")
    @PutMapping("/dict-items/{id}")
    @PreAuthorize("@ss.has('system:dict:update')")
    public CommonResult<Void> updateItem(@PathVariable Long id, @Valid @RequestBody ItemSave req) {
        dictService.updateItem(id, req);
        return CommonResult.success();
    }

    @OperLog("启用字典项")
    @PostMapping("/dict-items/{id}/enable")
    @PreAuthorize("@ss.has('system:dict:update')")
    public CommonResult<Void> enableItem(@PathVariable Long id) {
        dictService.changeItemStatus(id, EnableStatus.ENABLED);
        return CommonResult.success();
    }

    @OperLog("停用字典项")
    @PostMapping("/dict-items/{id}/disable")
    @PreAuthorize("@ss.has('system:dict:update')")
    public CommonResult<Void> disableItem(@PathVariable Long id) {
        dictService.changeItemStatus(id, EnableStatus.DISABLED);
        return CommonResult.success();
    }

    @OperLog("删除字典项")
    @DeleteMapping("/dict-items/{id}")
    @PreAuthorize("@ss.has('system:dict:delete')")
    public CommonResult<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return CommonResult.success();
    }

    /** 前端全局缓存，登录即可 */
    @GetMapping("/dicts/all")
    public CommonResult<DictBundle> all() {
        return CommonResult.success(dictService.bundle());
    }

    @GetMapping("/dicts/version")
    public CommonResult<Long> version() {
        return CommonResult.success(dictService.version());
    }

    @OperLog("刷新字典缓存")
    @PostMapping("/dicts/refresh-cache")
    @PreAuthorize("@ss.has('system:dict:update')")
    public CommonResult<Void> refreshCache() {
        dictService.refreshCache();
        return CommonResult.success();
    }
}
