package com.erp.module.engineering.controller;

import com.erp.common.result.CommonResult;
import com.erp.module.engineering.controller.vo.CategoryVOs.CategoryNode;
import com.erp.module.engineering.controller.vo.CategoryVOs.CategorySave;
import com.erp.module.engineering.controller.vo.CategoryVOs.SimpleNode;
import com.erp.module.engineering.service.MaterialCategoryService;
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

/** 物料类别（需求 05-01 第 6 节） */
@Tag(name = "研发工程 - 物料类别")
@RestController
@RequestMapping("/api/engineering/categories")
public class MaterialCategoryController {

    private final MaterialCategoryService categoryService;

    public MaterialCategoryController(MaterialCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "树形表格（含停用）")
    @GetMapping("/tree")
    @PreAuthorize("@ss.has('eng:category:query')")
    public CommonResult<List<CategoryNode>> tree(@RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return CommonResult.success(categoryService.tree(keyword, status));
    }

    @Operation(summary = "启用类别精简树（登录即可，供选择器）")
    @GetMapping("/simple-tree")
    public CommonResult<List<SimpleNode>> simpleTree() {
        return CommonResult.success(categoryService.simpleTree());
    }

    @Operation(summary = "新增下级时的默认排序")
    @GetMapping("/next-sort")
    @PreAuthorize("@ss.has('eng:category:create')")
    public CommonResult<Integer> nextSort(@RequestParam(required = false) Long parentId) {
        return CommonResult.success(categoryService.nextSort(parentId));
    }

    @PostMapping
    @PreAuthorize("@ss.has('eng:category:create')")
    public CommonResult<Long> create(@Valid @RequestBody CategorySave req) {
        return CommonResult.success(categoryService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('eng:category:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySave req) {
        categoryService.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('eng:category:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("@ss.has('eng:category:update')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        categoryService.enable(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("@ss.has('eng:category:update')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        categoryService.disable(id);
        return CommonResult.success();
    }
}
